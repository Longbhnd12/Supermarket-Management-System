package com.sms.backend.service.impl;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SystemLogResponse;
import com.sms.backend.entity.SystemLog;
import com.sms.backend.entity.User;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SystemLogRepository;
import com.sms.backend.service.SystemLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "createdAt", "action", "entityType", "entityId", "ipAddress"
    );
    private static final int MAX_PAGE_SIZE = 100;

    private final SystemLogRepository systemLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SystemLogResponse> getLogs(
            String search,
            String action,
            String entityType,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new com.sms.backend.exception.BadRequestException("From date cannot be after To date");
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String validSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        if (!ALLOWED_SORT_FIELDS.contains(validSortBy)) {
            throw new com.sms.backend.exception.BadRequestException("Unsupported sort field: " + validSortBy);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), Sort.by(direction, validSortBy));

        Specification<SystemLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate actionMatch = cb.like(cb.lower(root.get("action")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchPattern);
                Predicate entityMatch = cb.like(cb.lower(root.get("entityType")), searchPattern);
                Predicate ipMatch = cb.like(cb.lower(root.get("ipAddress")), searchPattern);
                predicates.add(cb.or(actionMatch, descMatch, entityMatch, ipMatch));
            }

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }

            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SystemLog> logPage = systemLogRepository.findAll(spec, pageable);
        Page<SystemLogResponse> dtoPage = logPage.map(this::mapToResponse);

        return PageResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemLogResponse getLogById(Long id) {
        SystemLog log = systemLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("System log not found with id: " + id));
        return mapToResponse(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctActions() {
        return systemLogRepository.findDistinctActions();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctEntityTypes() {
        return systemLogRepository.findDistinctEntityTypes();
    }

    @Override
    @Transactional
    public void createLog(User user, String action, String entityType, Long entityId, String description, String ipAddress) {
        SystemLog log = new SystemLog();
        log.setUser(user);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        systemLogRepository.save(log);
    }

    private SystemLogResponse mapToResponse(SystemLog entity) {
        Long userId = entity.getUser() != null ? entity.getUser().getUserId() : null;
        String username = entity.getUser() != null ? entity.getUser().getUsername() : null;

        return new SystemLogResponse(
                entity.getLogId(),
                userId,
                username,
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getDescription(),
                entity.getIpAddress(),
                entity.getCreatedAt()
        );
    }
}
