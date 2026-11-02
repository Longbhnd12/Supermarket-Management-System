package com.sms.backend.service.impl;

import com.sms.backend.dto.request.UpdateSystemSettingRequest;
import com.sms.backend.dto.response.SystemSettingResponse;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.service.SystemLogService;
import com.sms.backend.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final SystemLogService systemLogService;

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getAllSettings() {
        return systemSettingRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettingResponse getSettingById(Long id) {
        SystemSetting setting = systemSettingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("System setting not found with id: " + id));
        return mapToResponse(setting);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettingResponse getSettingByKey(String key) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("System setting not found with key: " + key));
        return mapToResponse(setting);
    }

    @Override
    @Transactional
    public SystemSettingResponse updateSetting(Long id, UpdateSystemSettingRequest request) {
        SystemSetting setting = systemSettingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("System setting not found with id: " + id));

        String oldValue = setting.getSettingValue();
        String newValue = request.settingValue().trim();

        // A6: Không có thay đổi thực tế - bỏ qua cập nhật và không ghi log
        if (oldValue != null && oldValue.equals(newValue)) {
            return mapToResponse(setting);
        }

        validateSettingValue(setting, newValue);

        User currentUser = resolveCurrentUser();

        setting.setSettingValue(newValue);
        setting.setUpdatedAt(LocalDateTime.now());
        if (currentUser != null) {
            setting.setUpdatedBy(currentUser);
        }

        SystemSetting saved = systemSettingRepository.save(setting);

        // Lưu log đồng thời trong cùng một transaction: format "KEY: oldValue → newValue"
        String logDescription = String.format("%s: %s → %s", setting.getSettingKey(), oldValue, newValue);
        String clientIp = extractClientIp();
        systemLogService.createLog(
                currentUser != null ? currentUser : saved.getUpdatedBy(),
                "UPDATE_SYSTEM_SETTING",
                "SYSTEM_SETTING",
                saved.getSettingId(),
                logDescription,
                clientIp
        );

        return mapToResponse(saved);
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        /*
         * Authentication is not wired into this project yet.  Keeping this resolver
         * in one place lets the settings audit trail receive the authenticated User
         * as soon as the authentication principal exposes it, without trusting a
         * user id supplied by the browser.
         */
        Object principal = authentication.getPrincipal();
        return principal instanceof User user ? user : null;
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest req = attributes.getRequest();
                String header = req.getHeader("X-Forwarded-For");
                if (header == null || header.isBlank()) {
                    return req.getRemoteAddr();
                }
                return header.split(",")[0].trim();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private void validateValueByDataType(String dataType, String value) {
        if (dataType == null || value == null) {
            return;
        }

        String type = dataType.toUpperCase().trim();
        String trimmedVal = value.trim();

        switch (type) {
            case "NUMBER", "INTEGER", "INT", "DOUBLE", "FLOAT" -> {
                try {
                    double number = Double.parseDouble(trimmedVal);
                    if (!Double.isFinite(number)) {
                        throw new NumberFormatException("Value must be finite");
                    }
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Value '" + value + "' is not a valid number for type " + dataType);
                }
            }
            case "BOOLEAN", "BOOL" -> {
                if (!trimmedVal.equalsIgnoreCase("true") && !trimmedVal.equalsIgnoreCase("false")) {
                    throw new BadRequestException("Value must be either 'true' or 'false' for boolean type");
                }
            }
        }
    }

    private void validateSettingValue(SystemSetting setting, String value) {
        validateValueByDataType(setting.getDataType(), value);

        String key = setting.getSettingKey() == null ? "" : setting.getSettingKey().toUpperCase();
        if (key.contains("STORE_NAME") && value.isBlank()) {
            throw new BadRequestException("Store name cannot be blank");
        }

        if (key.contains("THRESHOLD") || key.contains("WARNING")) {
            requireNumberAtLeast(value, 0, setting.getSettingKey());
        }
        if (key.contains("SESSION_TIMEOUT")) {
            requireNumberInRange(value, 1, 1440, setting.getSettingKey());
        }
        if (key.contains("MAX_FAILED_LOGIN_ATTEMPTS")) {
            requireNumberInRange(value, 1, 100, setting.getSettingKey());
        }
    }

    private void requireNumberAtLeast(String value, double minimum, String settingKey) {
        double number = parseNumber(value, settingKey);
        if (number < minimum) {
            throw new BadRequestException(settingKey + " must be at least " + minimum);
        }
    }

    private void requireNumberInRange(String value, double minimum, double maximum, String settingKey) {
        double number = parseNumber(value, settingKey);
        if (number < minimum || number > maximum) {
            throw new BadRequestException(settingKey + " must be between " + minimum + " and " + maximum);
        }
    }

    private double parseNumber(String value, String settingKey) {
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException("Value must be finite");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new BadRequestException(settingKey + " must be a valid number");
        }
    }

    private SystemSettingResponse mapToResponse(SystemSetting entity) {
        Long updatedById = entity.getUpdatedBy() != null ? entity.getUpdatedBy().getUserId() : null;
        String updatedByUsername = entity.getUpdatedBy() != null ? entity.getUpdatedBy().getUsername() : null;

        return new SystemSettingResponse(
                entity.getSettingId(),
                entity.getSettingKey(),
                entity.getSettingValue(),
                entity.getDataType(),
                updatedById,
                updatedByUsername,
                entity.getUpdatedAt()
        );
    }
}
