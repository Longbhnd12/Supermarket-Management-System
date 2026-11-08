package com.sms.backend.service;

import com.sms.backend.dto.response.NotificationResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.User;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.RoleName;

import java.math.BigDecimal;
import java.util.Set;

public interface NotificationService {
    PageResponse<NotificationResponse> getNotifications(String status, int page, int size);
    long getUnreadCount();
    NotificationResponse markAsRead(Long notificationId);

    void sendToUser(User user, NotificationType type, String title, String message);
    void sendToRole(RoleName roleName, NotificationType type, String title, String message);
    void sendToRoles(Set<RoleName> roleNames, NotificationType type, String title, String message);
    void sendToSupplier(Long supplierId, NotificationType type, String title, String message);
    void checkStockLevelTransition(Product product, BigDecimal previousStock, BigDecimal newStock);
    void sendStockReceiptConfirmed(StockReceipt receipt, PurchaseOrder purchaseOrder);
}
