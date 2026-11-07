package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.employee.EmployeeRequest;
import com.sms.backend.dto.employee.EmployeeResponse;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.SystemLog;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.SystemLogRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final SystemLogRepository systemLogRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public List<EmployeeResponse> getAllEmployees() {
        return employeeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<EmployeeResponse> getEmployeesByStatus(GeneralStatus status) {
        return employeeRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public EmployeeResponse getEmployeeById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        String code = request.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(code)) {
            throw new BadRequestException("Mã nhân viên đã tồn tại: " + code);
        }

        String email = request.email().trim();
        if (employeeRepository.existsByEmail(email)) {
            throw new BadRequestException("Email nhân viên đã tồn tại: " + email);
        }

        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFullName(request.fullName().trim());
        e.setDateOfBirth(request.dateOfBirth());
        e.setGender(request.gender());
        e.setPhone(request.phone().trim());
        e.setEmail(email);
        e.setAddress(request.address() != null ? request.address().trim() : null);
        e.setPosition(request.position().trim());
        e.setHireDate(request.hireDate());
        e.setStatus(GeneralStatus.ACTIVE);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        Employee saved = employeeRepository.save(e);
        logAction("EMPLOYEE_CREATED", saved.getEmployeeId(), "Tạo mới nhân viên: " + saved.getFullName() + " (" + saved.getEmployeeCode() + ")");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        Employee e = findById(id);

        String code = request.employeeCode().trim();
        if (!code.equalsIgnoreCase(e.getEmployeeCode()) && employeeRepository.existsByEmployeeCode(code)) {
            throw new BadRequestException("Mã nhân viên đã được sử dụng: " + code);
        }

        String email = request.email().trim();
        if (!email.equalsIgnoreCase(e.getEmail()) && employeeRepository.existsByEmail(email)) {
            throw new BadRequestException("Email nhân viên đã được sử dụng: " + email);
        }

        e.setEmployeeCode(code);
        e.setFullName(request.fullName().trim());
        e.setDateOfBirth(request.dateOfBirth());
        e.setGender(request.gender());
        e.setPhone(request.phone().trim());
        e.setEmail(email);
        e.setAddress(request.address() != null ? request.address().trim() : null);
        e.setPosition(request.position().trim());
        e.setHireDate(request.hireDate());
        e.setUpdatedAt(LocalDateTime.now());

        Employee saved = employeeRepository.save(e);
        logAction("EMPLOYEE_UPDATED", saved.getEmployeeId(), "Cập nhật thông tin nhân viên: " + saved.getFullName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EmployeeResponse updateStatus(Long id, GeneralStatus status) {
        Employee e = findById(id);
        GeneralStatus oldStatus = e.getStatus();
        e.setStatus(status);
        e.setUpdatedAt(LocalDateTime.now());
        Employee saved = employeeRepository.save(e);

        logAction("EMPLOYEE_STATUS_CHANGED", saved.getEmployeeId(),
                "Đổi trạng thái nhân viên " + saved.getEmployeeCode() + " từ " + oldStatus + " sang " + status);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        updateStatus(id, GeneralStatus.INACTIVE);
    }

    // --- helpers ---

    private Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + id));
    }

    private void logAction(String action, Long entityId, String description) {
        try {
            SystemLog log = new SystemLog();
            log.setUser(currentUserProvider.currentUser().orElse(null));
            log.setAction(action);
            log.setEntityType("Employee");
            log.setEntityId(entityId);
            log.setDescription(description);
            log.setIpAddress(currentUserProvider.clientIp());
            log.setCreatedAt(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception ignored) {
            // Audit log error must not fail business transaction
        }
    }

    private EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(
                e.getEmployeeId(),
                e.getEmployeeCode(),
                e.getFullName(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getPhone(),
                e.getEmail(),
                e.getAddress(),
                e.getPosition(),
                e.getHireDate(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
