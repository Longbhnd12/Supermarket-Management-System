package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.ApprovePurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.ApprovedItemRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderFilter;
import com.sms.backend.dto.purchaseorder.PurchaseOrderPageResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderReceiptSummary;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderStatusCountsResponse;
import com.sms.backend.dto.purchaseorder.RejectPurchaseOrderRequest;
import com.sms.backend.entity.Notification;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderDetailRepository purchaseOrderDetailRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final StockReceiptRepository stockReceiptRepository;

    @Override
    public List<PurchaseOrderResponse> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PurchaseOrderResponse> getPurchaseOrdersByStatus(PurchaseOrderStatus status) {
        return purchaseOrderRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PurchaseOrderPageResponse getPurchaseOrdersPage(PurchaseOrderFilter filter) {
        if (filter.startDate() != null && filter.endDate() != null && filter.startDate().isAfter(filter.endDate())) {
            throw new BadRequestException("Start date cannot be after end date");
        }

        LocalDateTime dateFrom = filter.startDate() != null ? filter.startDate().atStartOfDay() : null;
        LocalDateTime dateTo = filter.endDate() != null ? filter.endDate().atTime(LocalTime.MAX) : null;

        String keyword = filter.keyword() != null && !filter.keyword().isBlank()
                ? filter.keyword().trim()
                : null;

        int pageNum = Math.max(0, filter.page());
        int pageSize = filter.size() > 0 ? filter.size() : 10;
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize);

        Page<PurchaseOrder> page = purchaseOrderRepository.findAllByFilter(
                keyword,
                filter.supplierId(),
                filter.status(),
                dateFrom,
                dateTo,
                pageRequest
        );

        List<PurchaseOrderResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        PurchaseOrderStatusCountsResponse statusCounts = getStatusCounts(filter);

        return new PurchaseOrderPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                statusCounts
        );
    }

    @Override
    public PurchaseOrderStatusCountsResponse getStatusCounts(PurchaseOrderFilter filter) {
        LocalDateTime dateFrom = filter.startDate() != null ? filter.startDate().atStartOfDay() : null;
        LocalDateTime dateTo = filter.endDate() != null ? filter.endDate().atTime(LocalTime.MAX) : null;

        String keyword = filter.keyword() != null && !filter.keyword().isBlank()
                ? filter.keyword().trim()
                : null;

        List<Object[]> rows = purchaseOrderRepository.countByStatusWithFilter(
                keyword,
                filter.supplierId(),
                dateFrom,
                dateTo
        );

        long pendingApproval = 0;
        long rejectedByManager = 0;
        long sent = 0;
        long accepted = 0;
        long rejectedBySupplier = 0;
        long partiallyFulfilled = 0;
        long fulfilled = 0;
        long all = 0;

        for (Object[] row : rows) {
            PurchaseOrderStatus st = (PurchaseOrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            all += count;
            if (st == PurchaseOrderStatus.PENDING_APPROVAL) pendingApproval = count;
            else if (st == PurchaseOrderStatus.REJECTED_BY_MANAGER) rejectedByManager = count;
            else if (st == PurchaseOrderStatus.SENT) sent = count;
            else if (st == PurchaseOrderStatus.ACCEPTED) accepted = count;
            else if (st == PurchaseOrderStatus.REJECTED_BY_SUPPLIER) rejectedBySupplier = count;
            else if (st == PurchaseOrderStatus.PARTIALLY_FULFILLED) partiallyFulfilled = count;
            else if (st == PurchaseOrderStatus.FULFILLED) fulfilled = count;
        }

        return new PurchaseOrderStatusCountsResponse(
                all,
                pendingApproval,
                rejectedByManager,
                sent,
                accepted,
                rejectedBySupplier,
                partiallyFulfilled,
                fulfilled
        );
    }

    @Override
    public List<PurchaseOrderResponse> getPendingPurchaseRequests() {
        return getPurchaseOrdersByStatus(PurchaseOrderStatus.PENDING_APPROVAL);
    }

    @Override
    public PurchaseOrderResponse getPurchaseOrderById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public String getNextRequestNumber() {
        long nextId = purchaseOrderRepository.findTopByOrderByOrderIdDesc()
                .map(po -> po.getOrderId() + 1)
                .orElse(1L);
        String candidate = String.format("PR%04d", nextId);
        while (purchaseOrderRepository.existsByPoNumber(candidate)) {
            nextId++;
            candidate = String.format("PR%04d", nextId);
        }
        return candidate;
    }

    @Override
    public List<ProductResponse> getLowStockSuggestions() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(p -> new ProductResponse(
                        p.getProductId(),
                        p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                        p.getCategory() != null ? p.getCategory().getCategoryName() : null,
                        p.getProductCode(),
                        p.getBarcode(),
                        p.getProductName(),
                        p.getUnit(),
                        p.getSellingPrice(),
                        p.getStockQuantity(),
                        p.getMinimumStockLevel(),
                        p.getTrackExpiry(),
                        p.getStatus()
                ))
                .toList();
    }

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }

        // 1. Validate Supplier
        if (request.supplierId() == null) {
            throw new BadRequestException("Supplier ID is required");
        }
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.DISABLED) {
            throw new BadRequestException("Cannot create purchase request for disabled supplier: " + supplier.getSupplierName());
        }

        // 2. Validate Expected Delivery Date (must be in the future, > today)
        if (request.expectedDeliveryDate() == null) {
            throw new BadRequestException("Expected delivery date is required");
        }
        if (!request.expectedDeliveryDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Expected delivery date must be in the future");
        }

        // 3. Validate Note
        String note = request.note() != null ? request.note().trim() : null;
        if (note != null && note.isEmpty()) {
            note = null;
        }
        if (note != null && note.length() > 255) {
            throw new BadRequestException("Note must not exceed 255 characters");
        }

        // 4. Validate Details list
        if (request.details() == null || request.details().isEmpty()) {
            throw new BadRequestException("At least one product item is required");
        }

        // 5. Auto-generate or validate poNumber
        String poNumber = request.poNumber();
        if (poNumber == null || poNumber.isBlank()) {
            poNumber = getNextRequestNumber();
        } else {
            poNumber = poNumber.trim();
            if (poNumber.length() > 30) {
                throw new BadRequestException("Purchase request number must not exceed 30 characters");
            }
            if (purchaseOrderRepository.existsByPoNumber(poNumber)) {
                throw new BadRequestException("Purchase order number already exists: " + poNumber);
            }
        }

        // 6. Resolve Requester User
        User requester = resolveCurrentUser();

        // 7. Validate Products, check duplicates & calculate Totals
        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<Long> seenProductIds = new HashSet<>();
        List<ItemValidationContext> validatedItems = new ArrayList<>();

        for (PurchaseOrderDetailRequest itemReq : request.details()) {
            if (itemReq.productId() == null) {
                throw new BadRequestException("Product ID is required for all items");
            }
            if (!seenProductIds.add(itemReq.productId())) {
                throw new BadRequestException("Duplicate product in request: Product ID " + itemReq.productId());
            }

            BigDecimal qty = itemReq.effectiveQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Requested quantity must be greater than zero");
            }

            if (itemReq.unitPrice() == null || itemReq.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Estimated unit cost cannot be negative");
            }

            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));

            if (product.getStatus() != GeneralStatus.ACTIVE) {
                throw new BadRequestException("Cannot add inactive product to purchase request: " + product.getProductName());
            }

            BigDecimal subtotal = qty.multiply(itemReq.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(subtotal);

            validatedItems.add(new ItemValidationContext(product, qty, itemReq.unitPrice(), subtotal));
        }

        LocalDateTime now = LocalDateTime.now();

        // 8. Create and persist PurchaseOrder header
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poNumber);
        po.setSupplier(supplier);
        po.setRequestedBy(requester);
        po.setApprovedBy(null);
        po.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        po.setExpectedDeliveryDate(request.expectedDeliveryDate());
        po.setProposedDeliveryDate(null);
        po.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        po.setNote(note);
        po.setResponseReason(null);
        po.setCreatedAt(now);
        po.setUpdatedAt(null);

        po = purchaseOrderRepository.save(po);

        // 9. Create and persist PurchaseOrderDetail lines
        List<PurchaseOrderDetailResponse> detailResponses = new ArrayList<>();
        for (ItemValidationContext item : validatedItems) {
            PurchaseOrderDetail detail = new PurchaseOrderDetail();
            detail.setPurchaseOrder(po);
            detail.setProduct(item.product);
            detail.setRequestedQuantity(item.quantity);
            detail.setOrderedQuantity(null);
            detail.setProposedQuantity(null);
            detail.setReceivedQuantity(BigDecimal.ZERO);
            detail.setUnitPrice(item.unitPrice.setScale(2, RoundingMode.HALF_UP));
            detail.setProposedUnitPrice(null);

            detail = purchaseOrderDetailRepository.save(detail);

            detailResponses.add(new PurchaseOrderDetailResponse(
                    detail.getOrderDetailId(),
                    item.product.getProductId(),
                    item.product.getProductName(),
                    item.product.getProductCode(),
                    item.product.getUnit(),
                    item.quantity,
                    null,
                    detail.getUnitPrice(),
                    item.subtotal
            ));
        }

        return new PurchaseOrderResponse(
                po.getOrderId(),
                po.getPoNumber(),
                supplier.getSupplierId(),
                supplier.getSupplierName(),
                requester.getUserId(),
                requester.getUsername(),
                null,
                null,
                po.getTotalAmount(),
                po.getExpectedDeliveryDate(),
                po.getProposedDeliveryDate(),
                po.getStatus(),
                po.getNote(),
                po.getResponseReason(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                detailResponses
        );
    }

    // =========================================================================
    // UC47 — Approve Purchase Request
    // =========================================================================

    @Override
    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(Long id, ApprovePurchaseOrderRequest request) {
        PurchaseOrder po = findById(id);

        // 1. Validate status: only PENDING_APPROVAL requests may be approved
        if (po.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only purchase requests with status PENDING_APPROVAL can be approved. Current status: " + po.getStatus());
        }

        // 2. Load detail lines
        List<PurchaseOrderDetail> details = purchaseOrderDetailRepository.findByPurchaseOrder(po);
        if (details.isEmpty()) {
            throw new BadRequestException("Purchase request contains no item details");
        }

        // 3. Map user-adjusted approved quantities if provided
        Map<Long, BigDecimal> qtyByDetailId = new HashMap<>();
        Map<Long, BigDecimal> qtyByProductId = new HashMap<>();
        if (request != null && request.items() != null) {
            for (ApprovedItemRequest item : request.items()) {
                if (item.approvedQuantity() == null || item.approvedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Approved quantity must be greater than zero");
                }
                if (item.orderDetailId() != null) {
                    qtyByDetailId.put(item.orderDetailId(), item.approvedQuantity());
                } else if (item.productId() != null) {
                    qtyByProductId.put(item.productId(), item.approvedQuantity());
                }
            }
        }

        // 4. Update orderedQuantity on detail lines & recalculate total
        BigDecimal newTotal = BigDecimal.ZERO;
        for (PurchaseOrderDetail detail : details) {
            BigDecimal approvedQty = null;
            if (qtyByDetailId.containsKey(detail.getOrderDetailId())) {
                approvedQty = qtyByDetailId.get(detail.getOrderDetailId());
            } else if (qtyByProductId.containsKey(detail.getProduct().getProductId())) {
                approvedQty = qtyByProductId.get(detail.getProduct().getProductId());
            } else {
                // Default to original requested quantity
                approvedQty = detail.getRequestedQuantity();
            }

            if (approvedQty == null || approvedQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Approved quantity must be greater than zero for product: " + detail.getProduct().getProductName());
            }

            // Save approved quantity into ordered_quantity, preserving requested_quantity
            detail.setOrderedQuantity(approvedQty);
            BigDecimal lineTotal = approvedQty.multiply(detail.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            newTotal = newTotal.add(lineTotal);

            purchaseOrderDetailRepository.save(detail);
        }

        // 5. Resolve authenticated approver (Admin / Manager)
        User approver = resolveCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        // 6. Transition status to SENT and record approval metadata
        po.setApprovedBy(approver);
        po.setTotalAmount(newTotal.setScale(2, RoundingMode.HALF_UP));
        po.setStatus(PurchaseOrderStatus.SENT);
        if (request != null && request.managerNote() != null && !request.managerNote().isBlank()) {
            po.setResponseReason(request.managerNote().trim());
        }
        po.setUpdatedAt(now);

        po = purchaseOrderRepository.save(po);

        // 7. Persist system notifications
        try {
            Notification supplierNotif = new Notification();
            supplierNotif.setType(NotificationType.PO_ALERT);
            supplierNotif.setTargetRole("SUPPLIER");
            supplierNotif.setTitle("Purchase Order Issued: " + po.getPoNumber());
            supplierNotif.setMessage("Purchase Order " + po.getPoNumber() + " has been approved and issued to supplier " +
                    po.getSupplier().getSupplierName() + " for total amount " + po.getTotalAmount() + ".");
            supplierNotif.setIsRead(false);
            supplierNotif.setCreatedAt(now);
            notificationRepository.save(supplierNotif);

            if (po.getRequestedBy() != null) {
                Notification requesterNotif = new Notification();
                requesterNotif.setUser(po.getRequestedBy());
                requesterNotif.setType(NotificationType.PO_ALERT);
                requesterNotif.setTitle("Purchase Request Approved: " + po.getPoNumber());
                requesterNotif.setMessage("Your purchase request " + po.getPoNumber() + " was approved by " +
                        approver.getUsername() + " and sent to supplier.");
                requesterNotif.setIsRead(false);
                requesterNotif.setCreatedAt(now);
                notificationRepository.save(requesterNotif);
            }
        } catch (Exception e) {
            // Notification persistence failure should not roll back transaction
        }

        return toResponse(po);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse rejectPurchaseOrder(Long id, RejectPurchaseOrderRequest request) {
        PurchaseOrder po = findById(id);

        // 1. Validate status: only PENDING_APPROVAL requests may be rejected
        if (po.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only purchase requests with status PENDING_APPROVAL can be rejected. Current status: " + po.getStatus());
        }

        // 2. Validate mandatory rejection reason
        if (request == null || request.reason() == null || request.reason().trim().isEmpty()) {
            throw new BadRequestException("Rejection reason is mandatory when rejecting a purchase request");
        }
        String reason = request.reason().trim();
        if (reason.length() > 255) {
            throw new BadRequestException("Rejection reason must not exceed 255 characters");
        }

        // 3. Resolve authenticated approver
        User approver = resolveCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        // 4. Transition status to REJECTED_BY_MANAGER and save reason
        po.setStatus(PurchaseOrderStatus.REJECTED_BY_MANAGER);
        po.setApprovedBy(approver);
        po.setResponseReason(reason);
        po.setUpdatedAt(now);

        po = purchaseOrderRepository.save(po);

        // 5. Persist system notification for requester
        try {
            if (po.getRequestedBy() != null) {
                Notification requesterNotif = new Notification();
                requesterNotif.setUser(po.getRequestedBy());
                requesterNotif.setType(NotificationType.PO_ALERT);
                requesterNotif.setTitle("Purchase Request Rejected: " + po.getPoNumber());
                requesterNotif.setMessage("Your purchase request " + po.getPoNumber() + " was rejected by " +
                        approver.getUsername() + ". Reason: " + reason);
                requesterNotif.setIsRead(false);
                requesterNotif.setCreatedAt(now);
                notificationRepository.save(requesterNotif);
            }
        } catch (Exception e) {
            // Notification persistence failure should not roll back transaction
        }

        return toResponse(po);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long id) {
        throw new UnsupportedOperationException("Not yet implemented - pending SDD review");
    }

    @Override
    public List<PurchaseOrderResponse> getEligiblePurchaseOrdersBySupplier(Long supplierId) {
        if (supplierId == null) {
            throw new BadRequestException("Supplier ID is required");
        }
        return purchaseOrderRepository.findEligibleBySupplier(supplierId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // --- helpers ---

    private PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        List<PurchaseOrderDetailResponse> details = purchaseOrderDetailRepository.findByPurchaseOrder(po)
                .stream()
                .map(d -> {
                    BigDecimal effectiveQty = d.getOrderedQuantity() != null ? d.getOrderedQuantity() : d.getRequestedQuantity();
                    BigDecimal subtotal = effectiveQty != null && d.getUnitPrice() != null
                            ? effectiveQty.multiply(d.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new PurchaseOrderDetailResponse(
                            d.getOrderDetailId(),
                            d.getProduct().getProductId(),
                            d.getProduct().getProductName(),
                            d.getProduct().getProductCode(),
                            d.getProduct().getUnit(),
                            d.getRequestedQuantity(),
                            d.getOrderedQuantity(),
                            d.getReceivedQuantity() != null ? d.getReceivedQuantity() : BigDecimal.ZERO,
                            d.getUnitPrice(),
                            subtotal
                    );
                })
                .toList();

        List<PurchaseOrderReceiptSummary> receipts = stockReceiptRepository != null
                ? stockReceiptRepository.findByPurchaseOrderWithCreatedBy(po)
                        .stream()
                        .map(sr -> new PurchaseOrderReceiptSummary(
                                sr.getStockReceiptId(),
                                sr.getReceiptNumber(),
                                sr.getStatus(),
                                sr.getReceivedAt(),
                                sr.getCreatedAt(),
                                sr.getCreatedBy() != null ? sr.getCreatedBy().getUsername() : null
                        ))
                        .toList()
                : List.of();

        return new PurchaseOrderResponse(
                po.getOrderId(),
                po.getPoNumber(),
                po.getSupplier().getSupplierId(),
                po.getSupplier().getSupplierName(),
                po.getRequestedBy().getUserId(),
                po.getRequestedBy().getUsername(),
                po.getApprovedBy() != null ? po.getApprovedBy().getUserId() : null,
                po.getApprovedBy() != null ? po.getApprovedBy().getUsername() : null,
                po.getTotalAmount(),
                po.getExpectedDeliveryDate(),
                po.getProposedDeliveryDate(),
                po.getStatus(),
                po.getNote(),
                po.getResponseReason(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                details,
                receipts
        );
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            Optional<User> u = userRepository.findByUsername(auth.getName());
            if (u.isPresent()) return u.get();
        }
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No user found in database"));
    }

    private record ItemValidationContext(
            Product product,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
