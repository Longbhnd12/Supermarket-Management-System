package com.sms.backend.dto;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String errorCode,
        String message,
        String traceId
) {
}