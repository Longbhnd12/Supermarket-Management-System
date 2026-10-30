package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.NotificationResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.UnreadNotificationCountResponse;
import com.sms.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    @GetMapping public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) { return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", notificationService.getNotifications(status, page, size))); }
    @GetMapping("/unread-count") public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success("Unread notification count retrieved", new UnreadNotificationCountResponse(notificationService.getUnreadCount())));
    }
    @PatchMapping("/{id}/read") public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notificationService.markAsRead(id)));
    }
}
