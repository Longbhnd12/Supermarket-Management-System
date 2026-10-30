package com.sms.backend.dto.response;

import java.time.LocalDateTime;

public record SystemLogResponse(
        Long logId,
        Long userId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String description,
        String ipAddress,
        LocalDateTime createdAt
) {
}
