package com.sms.backend.dto.response;

import java.time.LocalDateTime;

public record SystemSettingResponse(
        Long settingId,
        String settingKey,
        String settingValue,
        String dataType,
        Long updatedById,
        String updatedByUsername,
        LocalDateTime updatedAt
) {
}
