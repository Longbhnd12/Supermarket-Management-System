package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Override
    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(Long id) {
        throw new UnsupportedOperationException("Not yet implemented - pending UC47");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse rejectPurchaseOrder(Long id, String reason) {
        throw new UnsupportedOperationException("Not yet implemented - pending UC47");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long id) {
        throw new UnsupportedOperationException("Not yet implemented - pending UC47");
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
                    BigDecimal qty = d.getRequestedQuantity() != null ? d.getRequestedQuantity() : d.getOrderedQuantity();
                    BigDecimal subtotal = qty != null && d.getUnitPrice() != null
                            ? qty.multiply(d.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new PurchaseOrderDetailResponse(
                            d.getOrderDetailId(),
                            d.getProduct().getProductId(),
                            d.getProduct().getProductName(),
                            d.getProduct().getProductCode(),
                            d.getProduct().getUnit(),
                            d.getRequestedQuantity(),
                            d.getOrderedQuantity(),
                            d.getUnitPrice(),
                            subtotal
                    );
                })
                .toList();

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
                details
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
