package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.response.NotificationResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.entity.Notification;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private enum StockLevelState {
        NORMAL,
        LOW_STOCK,
        OUT_OF_STOCK
    }

    @Override
    public PageResponse<NotificationResponse> getNotifications(String status, int page, int size) {
        User currentUser = currentUser();
        String role = role(currentUser);
        String normalized = normalizeStatus(status);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = "ALL".equals(normalized)
                ? notificationRepository.findForRecipient(currentUser, role, pageable)
                : notificationRepository.findForRecipientAndRead(currentUser, role, "READ".equals(normalized), pageable);
        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    public long getUnreadCount() {
        User currentUser = currentUser();
        return notificationRepository.countUnreadForRecipient(currentUser, role(currentUser));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        User currentUser = currentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!belongsTo(notification, currentUser)) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }
        notification.setIsRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void sendToUser(User user, NotificationType type, String title, String message) {
        if (user == null) {
            log.warn("Cannot send notification to null user");
            return;
        }
        if (user.getStatus() != null && user.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Cannot send notification to inactive user: {}", user.getUsername());
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTargetRole(null);
        notification.setType(type);
        notification.setTitle(title != null && title.length() > 200 ? title.substring(0, 200) : title);
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void sendToRole(RoleName roleName, NotificationType type, String title, String message) {
        if (roleName == null) return;
        List<User> activeUsers = userRepository.findByRole_RoleNameAndStatus(roleName, AccountStatus.ACTIVE);
        for (User u : activeUsers) {
            sendToUser(u, type, title, message);
        }
    }

    @Override
    @Transactional
    public void sendToRoles(Set<RoleName> roleNames, NotificationType type, String title, String message) {
        if (roleNames == null || roleNames.isEmpty()) return;
        Map<Long, User> userMap = new LinkedHashMap<>();
        for (RoleName rn : roleNames) {
            List<User> users = userRepository.findByRole_RoleNameAndStatus(rn, AccountStatus.ACTIVE);
            for (User u : users) {
                if (u.getUserId() != null) {
                    userMap.put(u.getUserId(), u);
                }
            }
        }
        for (User u : userMap.values()) {
            sendToUser(u, type, title, message);
        }
    }

    @Override
    @Transactional
    public void sendToSupplier(Long supplierId, NotificationType type, String title, String message) {
        if (supplierId == null) {
            log.warn("Cannot send notification to null supplierId");
            return;
        }
        Optional<User> supplierUserOpt = userRepository.findBySupplier_SupplierId(supplierId);
        if (supplierUserOpt.isEmpty()) {
            log.warn("No linked user account found for supplierId: {}. Notification skipped to preserve supplier privacy.", supplierId);
            return;
        }
        User supplierUser = supplierUserOpt.get();
        if (supplierUser.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Linked user account {} for supplierId: {} is not active. Notification skipped.", supplierUser.getUsername(), supplierId);
            return;
        }
        sendToUser(supplierUser, type, title, message);
    }

    @Override
    @Transactional
    public void checkStockLevelTransition(Product product, BigDecimal previousStock, BigDecimal newStock) {
        if (product == null) return;
        BigDecimal minLevel = product.getMinimumStockLevel() != null ? product.getMinimumStockLevel() : BigDecimal.ZERO;

        StockLevelState prevState = classifyStock(previousStock, minLevel);
        StockLevelState currState = classifyStock(newStock, minLevel);

        // Notify only on downward transitions
        if (prevState == StockLevelState.NORMAL && currState == StockLevelState.LOW_STOCK) {
            String title = "Stock Alert: Low Stock (" + product.getProductCode() + ")";
            String message = "Product " + product.getProductName() + " (" + product.getProductCode()
                    + ") has reached low stock level. Current quantity: " + newStock
                    + ", Minimum stock level: " + minLevel + ".";
            sendToRoles(Set.of(RoleName.WAREHOUSE_STAFF, RoleName.MANAGER), NotificationType.LOW_STOCK, title, message);
        } else if ((prevState == StockLevelState.NORMAL || prevState == StockLevelState.LOW_STOCK)
                && currState == StockLevelState.OUT_OF_STOCK) {
            String title = "Stock Alert: Out of Stock (" + product.getProductCode() + ")";
            String message = "Product " + product.getProductName() + " (" + product.getProductCode()
                    + ") is out of stock. Current quantity: " + newStock + ".";
            sendToRoles(Set.of(RoleName.WAREHOUSE_STAFF, RoleName.MANAGER), NotificationType.LOW_STOCK, title, message);
        }
    }

    @Override
    @Transactional
    public void sendStockReceiptConfirmed(StockReceipt receipt, PurchaseOrder purchaseOrder) {
        if (receipt == null || purchaseOrder == null) return;
        String receiptNumber = receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : ("#" + receipt.getStockReceiptId());
        String poNumber = purchaseOrder.getPoNumber() != null ? purchaseOrder.getPoNumber() : ("#" + purchaseOrder.getOrderId());
        String status = purchaseOrder.getStatus() != null ? purchaseOrder.getStatus().name() : "UPDATED";

        String title = "PO Receipt Confirmed: " + receiptNumber + " (" + poNumber + ")";
        String message = "Stock receipt " + receiptNumber + " has been confirmed for purchase order " + poNumber
                + ". Updated PO fulfillment status: " + status + ".";

        Map<Long, User> recipients = new LinkedHashMap<>();
        // Add active managers
        List<User> managers = userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE);
        for (User m : managers) {
            if (m.getUserId() != null) recipients.put(m.getUserId(), m);
        }
        // Add requester if active
        User requester = purchaseOrder.getRequestedBy();
        if (requester != null && requester.getUserId() != null && requester.getStatus() == AccountStatus.ACTIVE) {
            recipients.put(requester.getUserId(), requester);
        }

        for (User u : recipients.values()) {
            sendToUser(u, NotificationType.PO_ALERT, title, message);
        }
    }

    private StockLevelState classifyStock(BigDecimal stock, BigDecimal minLevel) {
        if (stock == null) return StockLevelState.NORMAL;
        if (stock.compareTo(BigDecimal.ZERO) <= 0) return StockLevelState.OUT_OF_STOCK;
        if (minLevel.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(minLevel) <= 0) return StockLevelState.LOW_STOCK;
        return StockLevelState.NORMAL;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedException("Authentication is required to access notifications");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user was not found"));
    }

    private String role(User user) {
        return user.getRole() == null || user.getRole().getRoleName() == null ? "" : user.getRole().getRoleName().name();
    }

    private boolean belongsTo(Notification notification, User user) {
        return notification.getUser() != null
                ? notification.getUser().getUserId().equals(user.getUserId())
                : role(user).equals(notification.getTargetRole());
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("ALL") && !value.equals("READ") && !value.equals("UNREAD")) {
            throw new BadRequestException("Unsupported notification status: " + status);
        }
        return value;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt()
        );
    }
}
