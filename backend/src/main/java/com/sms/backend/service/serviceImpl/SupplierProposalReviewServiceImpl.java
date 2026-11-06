package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.supplierportal.ReviewSupplierProposalRequest;
import com.sms.backend.dto.supplierportal.SupplierProposalReviewResponse;
import com.sms.backend.entity.Notification;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.User;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.SupplierPortalOrderRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.SupplierProposalReviewService;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Completes the UC49 proposal lifecycle without changing the existing staff PO flow.
 * A revised PO remains SENT, so the supplier must explicitly accept or reject the revised terms.
 */
@Service
@RequiredArgsConstructor
public class SupplierProposalReviewServiceImpl implements SupplierProposalReviewService {

    private final SupplierPortalOrderRepository orderRepository;
    private final PurchaseOrderDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemLogService systemLogService;
    private final Clock clock;

    @Override
    @Transactional
    public SupplierProposalReviewResponse review(Long orderId, ReviewSupplierProposalRequest request) {
        User reviewer = currentUserProvider.currentUser()
                .orElseThrow(() -> new AccountAccessException("AUTH_REQUIRED", "Authentication is required"));
        if (reviewer.getRole() == null || (reviewer.getRole().getRoleName() != RoleName.MANAGER
                && reviewer.getRole().getRoleName() != RoleName.ADMIN)) {
            throw new AccountAccessException("PROPOSAL_REVIEW_FORBIDDEN", "Only a Manager or Admin can review supplier proposals");
        }

        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + orderId));
        if (order.getStatus() != PurchaseOrderStatus.SENT) {
            throw new BadRequestException("Only a SENT purchase order can have a supplier proposal reviewed");
        }

        List<PurchaseOrderDetail> lines = orderRepository.findLines(orderId);
        if (!hasProposal(order, lines)) {
            throw new BadRequestException("This purchase order has no supplier proposal to review");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String comment = normalizeComment(request.comment());
        if (request.decision() == ReviewSupplierProposalRequest.Decision.ACCEPT) {
            applyProposal(order, lines);
        } else {
            clearProposal(order, lines);
        }

        order.setStatus(PurchaseOrderStatus.SENT);
        order.setResponseReason(comment);
        order.setUpdatedAt(now);
        detailRepository.saveAll(lines);
        orderRepository.save(order);

        String action = request.decision() == ReviewSupplierProposalRequest.Decision.ACCEPT
                ? "SUPPLIER_PO_PROPOSAL_ACCEPTED" : "SUPPLIER_PO_PROPOSAL_REJECTED";
        String message = request.decision() == ReviewSupplierProposalRequest.Decision.ACCEPT
                ? "Supplier proposal accepted; revised purchase order requires supplier confirmation."
                : "Supplier proposal rejected; original purchase order remains in effect.";
        systemLogService.createLog(reviewer, action, "PURCHASE_ORDER", order.getOrderId(),
                order.getPoNumber() + ": " + message, currentUserProvider.clientIp());
        notifySupplier(order, message, now);

        return new SupplierProposalReviewResponse(order.getOrderId(), order.getPoNumber(), order.getStatus(),
                request.decision(), order.getExpectedDeliveryDate(), order.getTotalAmount(), comment, now);
    }

    private boolean hasProposal(PurchaseOrder order, List<PurchaseOrderDetail> lines) {
        return order.getProposedDeliveryDate() != null || lines.stream().anyMatch(line ->
                line.getProposedQuantity() != null || line.getProposedUnitPrice() != null);
    }

    private void applyProposal(PurchaseOrder order, List<PurchaseOrderDetail> lines) {
        if (order.getProposedDeliveryDate() != null) {
            order.setExpectedDeliveryDate(order.getProposedDeliveryDate());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderDetail line : lines) {
            if (line.getProposedQuantity() != null) {
                line.setOrderedQuantity(line.getProposedQuantity());
            }
            if (line.getProposedUnitPrice() != null) {
                line.setUnitPrice(line.getProposedUnitPrice());
            }
            line.setProposedQuantity(null);
            line.setProposedUnitPrice(null);
            total = total.add(quantity(line).multiply(line.getUnitPrice()));
        }
        order.setTotalAmount(total);
        order.setProposedDeliveryDate(null);
    }

    private void clearProposal(PurchaseOrder order, List<PurchaseOrderDetail> lines) {
        order.setProposedDeliveryDate(null);
        lines.forEach(line -> {
            line.setProposedQuantity(null);
            line.setProposedUnitPrice(null);
        });
    }

    private BigDecimal quantity(PurchaseOrderDetail line) {
        return line.getOrderedQuantity() != null ? line.getOrderedQuantity() : line.getRequestedQuantity();
    }

    private String normalizeComment(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notifySupplier(PurchaseOrder order, String message, LocalDateTime now) {
        if (order.getSupplier() == null) {
            return;
        }
        userRepository.findBySupplier_SupplierId(order.getSupplier().getSupplierId()).ifPresent(supplierUser -> {
            Notification notification = new Notification();
            notification.setUser(supplierUser);
            notification.setType(NotificationType.PO_ALERT);
            notification.setTitle("Purchase Order review: " + order.getPoNumber());
            notification.setMessage(message);
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            notificationRepository.save(notification);
        });
    }
}
