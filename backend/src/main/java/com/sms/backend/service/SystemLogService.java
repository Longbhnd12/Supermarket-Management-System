package com.sms.backend.service;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SystemLogResponse;
import com.sms.backend.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogService {
    PageResponse<SystemLogResponse> getLogs(
            String search,
            String action,
            String entityType,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDirection
    );

    SystemLogResponse getLogById(Long id);

    List<String> getDistinctActions();

    List<String> getDistinctEntityTypes();

    void createLog(User user, String action, String entityType, Long entityId, String description, String ipAddress);
}
