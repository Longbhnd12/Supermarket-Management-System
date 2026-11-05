package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest;
import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest.ProposedItem;
import com.sms.backend.dto.supplierportal.SupplierOrderDetailResponse;
import com.sms.backend.dto.supplierportal.SupplierOrderItemResponse;
import com.sms.backend.dto.supplierportal.SupplierOrderPageResponse;
import com.sms.backend.dto.supplierportal.SupplierOrderStatusCounts;
import com.sms.backend.dto.supplierportal.SupplierOrderSummaryResponse;
import com.sms.backend.entity.Notification;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.Supplier;
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
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.SupplierPortalService;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sms.backend.enums.PurchaseOrderStatus.*;

/**
 * UC48 - View Purchase Orders (Supplier), UC49 - Respond to Purchase Order.
 * <ul>
 *     <li>UC48 BR-01: a supplier only sees the orders issued to its own supplier account.</li>
 *     <li>UC48 BR-02: only orders approved by a Manager (SENT or later) are visible.</li>
 *     <li>UC49 BR-02: a response is only possible while the status is SENT (AT4 otherwise read-only).</li>
 *     <li>UC49 BR-04/05: reason required for Reject / Propose; proposed values &gt; 0,
 *     proposed delivery date not earlier than the issued date.</li>
 *     <li>UC49 BR-06/07: Accept -> ACCEPTED, Reject -> REJECTED_BY_SUPPLIER, Propose -> stays SENT;
 *     every response is recorded with date/time and the store staff are notified.</li>
 * </ul>
 * "Issued date" = the creation date of the purchase order (the table has no separate issued column).
 */
@Service
@RequiredArgsConstructor
public class SupplierPortalServiceImpl implements SupplierPortalService {

    /** UC48 BR-02 */
    static final Set<PurchaseOrderStatus> VISIBLE_TO_SUPPLIER =
            EnumSet.of(SENT, ACCEPTED, REJECTED_BY_SUPPLIER, PARTIALLY_FULFILLED, FULFILLED);
    static final String MSG_NOT_A_SUPPLIER = "Your account is not linked to a supplier.";
    static final String MSG_NOT_FOUND = "Purchase order not found";
    static final String MSG_ALREADY_ANSWERED = "This purchase order can no longer be answered (it is not in Sent status).";
    static final String MSG_REASON_REQUIRED = "The Reason / Comment field is required.";
    static final String MSG_NO_CHANGE = "Enter at least one proposed change (quantity, unit price or delivery date).";
    private static final int MAX_PAGE_SIZE = 50;

    private final SupplierPortalOrderRepository orderRepository;
    private final PurchaseOrderDetailRepository detailRepository;
    private final NotificationRepository notificationRepository;
    private final SystemLogService systemLogService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    // ---------------- UC48 ----------------

    @Override
    @Transactional(readOnly = true)
    public SupplierOrderPageResponse getMyOrders(String keyword, PurchaseOrderStatus status,
                                                 LocalDate fromDate, LocalDate toDate, int page, int size) {
        Supplier supplier = currentSupplier();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("Start date cannot be later than end date");
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        SupplierOrderStatusCounts counts = counts(supplier.getSupplierId());

        if (status != null && !VISIBLE_TO_SUPPLIER.contains(status)) {
            return new SupplierOrderPageResponse(List.of(), safePage, safeSize, 0, 0, counts);
        }

        Set<PurchaseOrderStatus> statuses = status == null ? VISIBLE_TO_SUPPLIER : EnumSet.of(status);
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<PurchaseOrder> result = orderRepository.searchForSupplier(
                supplier.getSupplierId(), statuses, like,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "orderId")));

        Map<Long, long[]> lineStats = new HashMap<>();
        List<Long> ids = result.getContent().stream().map(PurchaseOrder::getOrderId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : orderRepository.lineStatsForOrders(ids)) {
                long lines = ((Number) row[1]).longValue();
                long proposed = row[2] == null ? 0 : ((Number) row[2]).longValue();
                lineStats.put((Long) row[0], new long[]{lines, proposed});
            }
        }

        List<SupplierOrderSummaryResponse> content = result.getContent().stream()
                .map(po -> {
                    long[] stats = lineStats.getOrDefault(po.getOrderId(), new long[]{0, 0});
                    boolean hasProposal = po.getStatus() == SENT && (stats[1] > 0 || po.getProposedDeliveryDate() != null);
                    return new SupplierOrderSummaryResponse(po.getOrderId(), po.getPoNumber(), po.getCreatedAt(),
                            po.getExpectedDeliveryDate(), (int) stats[0], po.getTotalAmount(), po.getStatus(), hasProposal);
                })
                .toList();

        return new SupplierOrderPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), counts);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierOrderDetailResponse getMyOrder(Long orderId) {
        Supplier supplier = currentSupplier();
        PurchaseOrder po = orderRepository.findById(orderId)
                .filter(order -> belongsTo(order, supplier))
                .orElseThrow(() -> new ResourceNotFoundException(MSG_NOT_FOUND));
        return toDetail(po, orderRepository.findLines(orderId));
    }

    // ---------------- UC49 ----------------

    @Override
    @Transactional
    public SupplierOrderDetailResponse respond(Long orderId, RespondPurchaseOrderRequest request) {
        Supplier supplier = currentSupplier();
        PurchaseOrder po = orderRepository.findByIdForUpdate(orderId)
                .filter(order -> belongsTo(order, supplier))
                .orElseThrow(() -> new ResourceNotFoundException(MSG_NOT_FOUND));
        if (po.getStatus() != SENT) {
            throw new BadRequestException(MSG_ALREADY_ANSWERED); // BR-02 / AT4
        }

        List<PurchaseOrderDetail> lines = orderRepository.findLines(orderId);
        String reason = request.reason() == null ? null : request.reason().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        String logAction;
        String summary;

        switch (request.action()) {
            case ACCEPT -> {
                clearProposals(po, lines);
                po.setStatus(ACCEPTED);
                po.setResponseReason(reason == null || reason.isEmpty() ? null : reason);
                logAction = "SUPPLIER_PO_ACCEPTED";
                summary = "accepted";
            }
            case REJECT -> {
                requireReason(reason);
                clearProposals(po, lines);
                po.setStatus(REJECTED_BY_SUPPLIER);
                po.setResponseReason(reason);
                logAction = "SUPPLIER_PO_REJECTED";
                summary = "rejected";
            }
            case PROPOSE_CHANGES -> {
                requireReason(reason);
                applyProposal(po, lines, request);
                po.setResponseReason(reason);
                // status stays SENT: the store reviews the proposal (AT2)
                logAction = "SUPPLIER_PO_CHANGES_PROPOSED";
                summary = "proposed changes to";
            }
            default -> throw new BadRequestException("Unsupported response option");
        }

        po.setUpdatedAt(now);
        detailRepository.saveAll(lines);
        orderRepository.save(po);

        User actor = currentUserProvider.currentUser().orElse(null);
        String text = "Supplier " + supplier.getSupplierName() + " " + summary + " purchase order " + po.getPoNumber()
                + (reason == null || reason.isEmpty() ? "." : ". Reason: " + reason);
        systemLogService.createLog(actor, logAction, "PURCHASE_ORDER", po.getOrderId(), text, currentUserProvider.clientIp());
        notifyStore(po, summary, text, now);

        return toDetail(po, lines);
    }

    // ---------------- helpers ----------------

    private void applyProposal(PurchaseOrder po, List<PurchaseOrderDetail> lines, RespondPurchaseOrderRequest request) {
        Map<Long, PurchaseOrderDetail> byId = lines.stream()
                .collect(Collectors.toMap(PurchaseOrderDetail::getOrderDetailId, line -> line));
        boolean changed = false;

        List<ProposedItem> items = request.items() == null ? List.of() : request.items();
        for (ProposedItem item : items) {
            PurchaseOrderDetail line = byId.get(item.orderDetailId());
            if (line == null) {
                throw new BadRequestException("Order line " + item.orderDetailId() + " does not belong to this purchase order");
            }
            BigDecimal qty = positiveOrNull(item.proposedQuantity(), "Proposed quantity");
            BigDecimal price = positiveOrNull(item.proposedUnitPrice(), "Proposed unit price");

            // Only keep values that are really different from the order
            line.setProposedQuantity(qty != null && qty.compareTo(orderedQuantity(line)) != 0 ? qty : null);
            line.setProposedUnitPrice(price != null && price.compareTo(line.getUnitPrice()) != 0 ? price : null);
            changed |= line.getProposedQuantity() != null || line.getProposedUnitPrice() != null;
        }

        LocalDate deliveryDate = request.proposedDeliveryDate();
        if (deliveryDate != null) {
            LocalDate issued = po.getCreatedAt() == null ? null : po.getCreatedAt().toLocalDate();
            if (issued != null && deliveryDate.isBefore(issued)) {
                throw new BadRequestException("Proposed delivery date must not be earlier than the issued date");
            }
            po.setProposedDeliveryDate(deliveryDate.equals(po.getExpectedDeliveryDate()) ? null : deliveryDate);
        } else {
            po.setProposedDeliveryDate(null);
        }
        changed |= po.getProposedDeliveryDate() != null;

        if (!changed) {
            throw new BadRequestException(MSG_NO_CHANGE);
        }
    }

    private BigDecimal positiveOrNull(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BadRequestException(label + " must be greater than zero");
        }
        return value;
    }

    private void clearProposals(PurchaseOrder po, List<PurchaseOrderDetail> lines) {
        po.setProposedDeliveryDate(null);
        lines.forEach(line -> {
            line.setProposedQuantity(null);
            line.setProposedUnitPrice(null);
        });
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            throw new BadRequestException(MSG_REASON_REQUIRED); // BR-04
        }
    }

    /** The requester and every Manager are told about the response (UC49 step 8, BR-07). */
    private void notifyStore(PurchaseOrder po, String summary, String text, LocalDateTime now) {
        String title = "Supplier " + summary + " " + po.getPoNumber();
        if (po.getRequestedBy() != null) {
            notificationRepository.save(notification(po.getRequestedBy(), null, title, text, now));
        }
        notificationRepository.save(notification(null, RoleName.MANAGER.name(), title, text, now));
    }

    private Notification notification(User user, String targetRole, String title, String text, LocalDateTime now) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTargetRole(targetRole);
        notification.setType(NotificationType.PO_ALERT);
        notification.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        notification.setMessage(text);
        notification.setIsRead(false);
        notification.setCreatedAt(now);
        return notification;
    }

    private Supplier currentSupplier() {
        User user = currentUserProvider.currentUser()
                .orElseThrow(() -> new AccountAccessException("NOT_A_SUPPLIER", MSG_NOT_A_SUPPLIER));
        boolean supplierRole = user.getRole() != null && user.getRole().getRoleName() == RoleName.SUPPLIER;
        if (!supplierRole || user.getSupplier() == null) {
            throw new AccountAccessException("NOT_A_SUPPLIER", MSG_NOT_A_SUPPLIER);
        }
        return user.getSupplier();
    }

    private boolean belongsTo(PurchaseOrder po, Supplier supplier) {
        return po.getSupplier() != null
                && Objects.equals(po.getSupplier().getSupplierId(), supplier.getSupplierId())
                && VISIBLE_TO_SUPPLIER.contains(po.getStatus());
    }

    private SupplierOrderStatusCounts counts(Long supplierId) {
        Map<PurchaseOrderStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : orderRepository.countByStatusForSupplier(supplierId)) {
            byStatus.put((PurchaseOrderStatus) row[0], ((Number) row[1]).longValue());
        }
        return new SupplierOrderStatusCounts(
                byStatus.getOrDefault(SENT, 0L),
                byStatus.getOrDefault(ACCEPTED, 0L),
                byStatus.getOrDefault(REJECTED_BY_SUPPLIER, 0L),
                byStatus.getOrDefault(PARTIALLY_FULFILLED, 0L),
                byStatus.getOrDefault(FULFILLED, 0L));
    }

    private BigDecimal orderedQuantity(PurchaseOrderDetail line) {
        if (line.getOrderedQuantity() != null) return line.getOrderedQuantity();
        return line.getRequestedQuantity() == null ? BigDecimal.ZERO : line.getRequestedQuantity();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private SupplierOrderDetailResponse toDetail(PurchaseOrder po, List<PurchaseOrderDetail> lines) {
        BigDecimal proposedTotal = BigDecimal.ZERO;
        List<SupplierOrderItemResponse> items = new java.util.ArrayList<>();
        for (PurchaseOrderDetail line : lines) {
            BigDecimal ordered = orderedQuantity(line);
            BigDecimal price = nz(line.getUnitPrice());
            BigDecimal effectiveQty = line.getProposedQuantity() != null ? line.getProposedQuantity() : ordered;
            BigDecimal effectivePrice = line.getProposedUnitPrice() != null ? line.getProposedUnitPrice() : price;
            proposedTotal = proposedTotal.add(effectiveQty.multiply(effectivePrice));
            items.add(new SupplierOrderItemResponse(
                    line.getOrderDetailId(),
                    line.getProduct() == null ? null : line.getProduct().getProductId(),
                    line.getProduct() == null ? null : line.getProduct().getProductCode(),
                    line.getProduct() == null ? null : line.getProduct().getProductName(),
                    line.getProduct() == null ? null : line.getProduct().getUnit(),
                    ordered,
                    price,
                    line.getProposedQuantity(),
                    line.getProposedUnitPrice(),
                    ordered.multiply(price)));
        }
        return new SupplierOrderDetailResponse(
                po.getOrderId(),
                po.getPoNumber(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getExpectedDeliveryDate(),
                po.getApprovedBy() == null ? null : po.getApprovedBy().getUsername(),
                po.getNote(),
                po.getTotalAmount(),
                po.getProposedDeliveryDate(),
                proposedTotal,
                po.getResponseReason(),
                po.getUpdatedAt(),
                po.getStatus() == SENT,
                items);
    }
}
