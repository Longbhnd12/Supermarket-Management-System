package com.sms.backend.service;

import com.sms.backend.dto.response.NotificationResponse;
import com.sms.backend.dto.response.PageResponse;

public interface NotificationService {
    PageResponse<NotificationResponse> getNotifications(String status, int page, int size);
    long getUnreadCount();
    NotificationResponse markAsRead(Long notificationId);
}
