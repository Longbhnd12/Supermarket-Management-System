package com.sms.backend.dto.response;

import com.sms.backend.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId, NotificationType type, String title, String message,
        boolean isRead, LocalDateTime createdAt
) {}
