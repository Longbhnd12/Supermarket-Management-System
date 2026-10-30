package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.inventory.CreateStockReceiptItemRequest;
import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptDetailResponse;
import com.sms.backend.dto.inventory.StockReceiptFilter;
import com.sms.backend.dto.inventory.StockReceiptListItem;
import com.sms.backend.dto.inventory.StockReceiptPageResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.StockCheckRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    /**
     * Days-ahead window for NEAR_EXPIRY detection.
     * LIMITATION: SRS/SDD says this is a system_setting. Hard-coded to 30 days
     * until UC35 (system settings) is implemented.
     */
    private static final int NEAR_EXPIRY_DAYS = 30;

    private final StockReceiptRepository stockReceiptRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockCheckRepository stockCheckRepository;
    private final ProductRepository productRepository;
    private final StockReceiptDetailRepository stockReceiptDetailRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    // =========================================================================
    // UC31 - Inventory List
    // =========================================================================

    @Override
    public InventoryPageResponse getInventoryList(InventoryFilter filter) {
        String kw = filter.normalizedKeyword();
        int size = filter.clampedSize();
        int page = Math.max(filter.page(), 0);
        String statusFilter = filter.stockStatus();

        if (statusFilter != null && !statusFilter.isBlank()) {
            List<Product> all = productRepository.findAllByFilterNoPaging(kw, filter.categoryId());
            List<InventoryResponse> filteredRows = all.stream()
                    .map(this::toInventoryResponse)
                    .filter(r -> statusFilter.equalsIgnoreCase(r.stockStatus()))
                    .collect(Collectors.toList());

            long totalElements = filteredRows.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, (int) totalElements);
            int toIndex = Math.min(fromIndex + size, (int) totalElements);
            List<InventoryResponse> pageRows = filteredRows.subList(fromIndex, toIndex);

            return new InventoryPageResponse(
                    pageRows,
                    page,
                    size,
                    totalElements,
                    totalPages
            );
        }

        Page<Product> productPage = productRepository.findAllByFilter(
                kw,
                filter.categoryId(),
                PageRequest.of(page, size, Sort.by("productName").ascending())
        );

        List<InventoryResponse> rows = productPage.getContent().stream()
                .map(this::toInventoryResponse)
                .collect(Collectors.toList());

        return new InventoryPageResponse(
                rows,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );
    }

    @Override
    public byte[] exportInventoryCsv(InventoryFilter filter) {
        String kw = filter.normalizedKeyword();
        List<Product> all = productRepository.findAllByFilterNoPaging(kw, filter.categoryId());
        String statusFilter = filter.stockStatus();
        List<InventoryResponse> rows = all.stream()
                .map(this::toInventoryResponse)
                .filter(r -> statusFilter == null || statusFilter.isBlank()
                        || statusFilter.equalsIgnoreCase(r.stockStatus()))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("SKU,Barcode,Product Name,Category,On Hand,Safety Min,Status\n");
        for (InventoryResponse r : rows) {
            sb.append(csvEscape(r.productCode())).append(',');
            sb.append(csvEscape(r.barcode())).append(',');
            sb.append(csvEscape(r.productName())).append(',');
            sb.append(csvEscape(r.categoryName())).append(',');
            sb.append(r.stockQuantity()).append(',');
            sb.append(r.minimumStockLevel()).append(',');
            sb.append(csvEscape(r.stockStatus())).append('\n');
        }
        byte[] bom = new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    // =========================================================================
    // Stock status derivation (SRS Â§3.10.1)
    // =========================================================================

    private String deriveStockStatus(Product p) {
        BigDecimal qty = p.getStockQuantity();
        BigDecimal minLevel = p.getMinimumStockLevel();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return "OUT_OF_STOCK";
        if (Boolean.TRUE.equals(p.getTrackExpiry())) {
            LocalDate threshold = LocalDate.now().plusDays(NEAR_EXPIRY_DAYS);
            boolean nearExpiry = stockReceiptDetailRepository.findByProduct(p).stream()
                    .filter(d -> d.getStockReceipt().getStatus() == StockReceiptStatus.CONFIRMED)
                    .filter(d -> d.getRemainingQuantity() != null
                              && d.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .anyMatch(d -> d.getExpirationDate() != null
                               && !d.getExpirationDate().isBefore(LocalDate.now())
                               && !d.getExpirationDate().isAfter(threshold));
            if (nearExpiry) return "NEAR_EXPIRY";
        }
        if (qty.compareTo(minLevel) <= 0) return "LOW_STOCK";
        return "IN_STOCK";
    }

    private InventoryResponse toInventoryResponse(Product p) {
        return new InventoryResponse(
                p.getProductId(),
                p.getProductCode(),
                p.getBarcode(),
                p.getProductName(),
                p.getCategory().getCategoryId(),
                p.getCategory().getCategoryName(),
                p.getUnit(),
                p.getStockQuantity(),
                p.getMinimumStockLevel(),
                deriveStockStatus(p)
        );
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // =========================================================================
    // UC37 - Stock Receipt List
    // =========================================================================

    @Override
    public StockReceiptPageResponse getStockReceiptList(StockReceiptFilter filter) {
        int size = filter.clampedSize();
        int page = Math.max(filter.page(), 0);

        Page<StockReceipt> receiptPage = stockReceiptRepository.findAllByFilter(
                filter.normalizedKeyword(),
                filter.normalizedProductSku(),
                filter.supplierId(),
                filter.status(),
                filter.effectiveDateFrom(),
                filter.effectiveDateTo(),
                PageRequest.of(page, size)
        );

        List<StockReceiptListItem> rows = receiptPage.getContent().stream()
                .map(this::toReceiptListItem)
                .collect(Collectors.toList());

        return new StockReceiptPageResponse(
                rows,
                receiptPage.getNumber(),
                receiptPage.getSize(),
                receiptPage.getTotalElements(),
                receiptPage.getTotalPages()
        );
    }

    // =========================================================================
    // StockReceipt (legacy / detail)
    // =========================================================================

    @Override
    public List<StockReceiptResponse> getAllStockReceipts() {
        return stockReceiptRepository.findAll().stream().map(this::toReceiptResponse).toList();
    }

    @Override
    public List<StockReceiptResponse> getStockReceiptsByStatus(StockReceiptStatus status) {
        return stockReceiptRepository.findByStatus(status).stream().map(this::toReceiptResponse).toList();
    }

    @Override
    public StockReceiptResponse getStockReceiptById(Long id) {
        return toReceiptResponse(findReceiptById(id));
    }

    @Override
    @Transactional
    public StockReceiptResponse createStockReceipt(CreateStockReceiptRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Stock receipt must contain at least one product item");
        }

        // 1. Validate Supplier
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.DISABLED) {
            throw new BadRequestException("Cannot import stock from disabled supplier: " + supplier.getSupplierName());
        }

        // 2. Validate Optional Purchase Order
        PurchaseOrder purchaseOrder = null;
        if (request.purchaseOrderId() != null) {
            purchaseOrder = purchaseOrderRepository.findById(request.purchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + request.purchaseOrderId()));
        }

        // 3. Resolve Creator User
        User creator = resolveCurrentUser();

        // 4. Validate or generate receiptNumber
        String receiptNumber = request.receiptNumber();
        if (receiptNumber == null || receiptNumber.isBlank()) {
            receiptNumber = getNextReceiptNumber();
        } else {
            receiptNumber = receiptNumber.trim();
            if (receiptNumber.length() > 50) {
                throw new BadRequestException("Receipt number must not exceed 50 characters");
            }
            if (stockReceiptRepository.existsByReceiptNumber(receiptNumber)) {
                throw new BadRequestException("Stock receipt number already exists: " + receiptNumber);
            }
        }

        // Target status: default to CONFIRMED if not specified
        StockReceiptStatus targetStatus = request.status() != null ? request.status() : StockReceiptStatus.CONFIRMED;
        LocalDateTime now = LocalDateTime.now();

        // 5. Pre-validate products & calculate subtotals
        BigDecimal totalCost = BigDecimal.ZERO;
        List<ItemValidationContext> validatedItems = new ArrayList<>();

        for (CreateStockReceiptItemRequest itemReq : request.items()) {
            if (itemReq.quantity() == null || itemReq.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }
            if (itemReq.unitCost() == null || itemReq.unitCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Unit cost must not be negative");
            }

            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));

            if (product.getStatus() != GeneralStatus.ACTIVE) {
                throw new BadRequestException("Cannot import inactive product: " + product.getProductName());
            }

            String batchNumber = itemReq.batchNumber() != null ? itemReq.batchNumber().trim() : null;
            if (batchNumber != null && batchNumber.isEmpty()) batchNumber = null;
            LocalDate expirationDate = itemReq.expirationDate();

            if (Boolean.TRUE.equals(product.getTrackExpiry())) {
                if (batchNumber == null) {
                    throw new BadRequestException("Batch number is required for perishable product: " + product.getProductName());
                }
                if (batchNumber.length() > 50) {
                    throw new BadRequestException("Batch number must not exceed 50 characters: " + product.getProductName());
                }
                if (expirationDate == null) {
                    throw new BadRequestException("Expiration date is required for perishable product: " + product.getProductName());
                }
                if (expirationDate.isBefore(LocalDate.now())) {
                    throw new BadRequestException("Expiration date cannot be in the past for product: " + product.getProductName());
                }
            }

            BigDecimal subtotal = itemReq.quantity().multiply(itemReq.unitCost()).setScale(2, RoundingMode.HALF_UP);
            totalCost = totalCost.add(subtotal);

            validatedItems.add(new ItemValidationContext(product, itemReq.quantity(), itemReq.unitCost(), batchNumber, expirationDate, subtotal));
        }

        // 6. Build and save StockReceipt header
        StockReceipt receipt = new StockReceipt();
        receipt.setReceiptNumber(receiptNumber);
        receipt.setSupplier(supplier);
        receipt.setPurchaseOrder(purchaseOrder);
        receipt.setCreatedBy(creator);
        receipt.setTotalCost(totalCost);
        receipt.setStatus(targetStatus);
        receipt.setCreatedAt(now);
        receipt.setReceivedAt(targetStatus == StockReceiptStatus.CONFIRMED ? now : null);

        receipt = stockReceiptRepository.save(receipt);

        // 7. Save Details, update product stock & record StockMovement (if CONFIRMED)
        for (ItemValidationContext item : validatedItems) {
            StockReceiptDetail detail = new StockReceiptDetail();
            detail.setStockReceipt(receipt);
            detail.setProduct(item.product);
            detail.setQuantity(item.quantity);
            detail.setRemainingQuantity(targetStatus == StockReceiptStatus.CONFIRMED ? item.quantity : BigDecimal.ZERO);
            detail.setUnitCost(item.unitCost);
            detail.setBatchNumber(item.batchNumber);
            detail.setExpirationDate(item.expirationDate);
            detail.setSubtotal(item.subtotal);

            detail = stockReceiptDetailRepository.save(detail);

            if (targetStatus == StockReceiptStatus.CONFIRMED) {
                // Update product stock
                Product p = item.product;
                BigDecimal currentStock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
                p.setStockQuantity(currentStock.add(item.quantity));
                productRepository.save(p);

                // Record stock movement
                StockMovement movement = new StockMovement();
                movement.setProduct(p);
                movement.setStockReceiptDetail(detail);
                movement.setMovementType(StockMovementType.RECEIPT);
                movement.setQuantityChange(item.quantity);
                movement.setReferenceType(StockReferenceType.STOCK_RECEIPT);
                movement.setReferenceId(receipt.getStockReceiptId());
                movement.setReason("Stock receipt " + receipt.getReceiptNumber());
                movement.setCreatedBy(creator);
                movement.setCreatedAt(now);
                stockMovementRepository.save(movement);
            }
        }

        return getStockReceiptById(receipt.getStockReceiptId());
    }

    @Override
    @Transactional
    public StockReceiptResponse confirmStockReceipt(Long id) {
        StockReceipt receipt = findReceiptById(id);

        if (receipt.getStatus() == StockReceiptStatus.CONFIRMED) {
            throw new BusinessException("Stock receipt is already confirmed");
        }
        if (receipt.getStatus() == StockReceiptStatus.CANCELLED) {
            throw new BusinessException("Cannot confirm a cancelled stock receipt");
        }

        LocalDateTime now = LocalDateTime.now();
        receipt.setStatus(StockReceiptStatus.CONFIRMED);
        receipt.setReceivedAt(now);
        receipt = stockReceiptRepository.save(receipt);

        User operator = resolveCurrentUser();

        List<StockReceiptDetail> details = stockReceiptDetailRepository.findByStockReceipt(receipt);
        for (StockReceiptDetail detail : details) {
            detail.setRemainingQuantity(detail.getQuantity());
            stockReceiptDetailRepository.save(detail);

            // Update product inventory
            Product p = detail.getProduct();
            BigDecimal currentStock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
            p.setStockQuantity(currentStock.add(detail.getQuantity()));
            productRepository.save(p);

            // Log StockMovement
            StockMovement movement = new StockMovement();
            movement.setProduct(p);
            movement.setStockReceiptDetail(detail);
            movement.setMovementType(StockMovementType.RECEIPT);
            movement.setQuantityChange(detail.getQuantity());
            movement.setReferenceType(StockReferenceType.STOCK_RECEIPT);
            movement.setReferenceId(receipt.getStockReceiptId());
            movement.setReason("Stock receipt " + receipt.getReceiptNumber());
            movement.setCreatedBy(operator);
            movement.setCreatedAt(now);
            stockMovementRepository.save(movement);
        }

        return getStockReceiptById(receipt.getStockReceiptId());
    }

    @Override
    public String getNextReceiptNumber() {
        long nextId = stockReceiptRepository.findTopByOrderByStockReceiptIdDesc()
                .map(sr -> sr.getStockReceiptId() + 1)
                .orElse(1L);
        String candidate = String.format("SR%04d", nextId);
        while (stockReceiptRepository.existsByReceiptNumber(candidate)) {
            nextId++;
            candidate = String.format("SR%04d", nextId);
        }
        return candidate;
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
            BigDecimal unitCost,
            String batchNumber,
            LocalDate expirationDate,
            BigDecimal subtotal
    ) {}

    // =========================================================================
    // StockMovement
    // =========================================================================

    @Override
    public List<StockMovementResponse> getMovementsByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return stockMovementRepository.findByProduct(product).stream().map(this::toMovementResponse).toList();
    }

    @Override
    public List<StockMovementResponse> getMovementsByType(StockMovementType movementType) {
        return stockMovementRepository.findByMovementType(movementType).stream().map(this::toMovementResponse).toList();
    }

    @Override
    public List<StockMovementResponse> getMovementsByReference(StockReferenceType referenceType, Long referenceId) {
        return stockMovementRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId)
                .stream().map(this::toMovementResponse).toList();
    }

    // =========================================================================
    // StockCheck
    // =========================================================================

    @Override
    public List<StockCheckResponse> getAllStockChecks() {
        return stockCheckRepository.findAll().stream().map(this::toCheckResponse).toList();
    }

    @Override
    public StockCheckResponse getStockCheckById(Long id) {
        return toCheckResponse(findCheckById(id));
    }

    @Override
    public List<StockCheckResponse> getStockChecksByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return stockCheckRepository.findByProduct(product).stream().map(this::toCheckResponse).toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StockReceipt findReceiptById(Long id) {
        return stockReceiptRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockReceipt not found: " + id));
    }

    private StockCheck findCheckById(Long id) {
        return stockCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockCheck not found: " + id));
    }

    private StockReceiptListItem toReceiptListItem(StockReceipt sr) {
        return new StockReceiptListItem(
                sr.getStockReceiptId(),
                sr.getReceiptNumber(),
                sr.getSupplier().getSupplierId(),
                sr.getSupplier().getSupplierName(),
                sr.getTotalCost(),
                sr.getStatus(),
                sr.getReceivedAt(),
                sr.getCreatedAt(),
                sr.getCreatedBy().getUsername()
        );
    }

    private StockReceiptResponse toReceiptResponse(StockReceipt sr) {
        List<StockReceiptDetailResponse> detailItems =
                stockReceiptDetailRepository.findByStockReceipt(sr).stream()
                        .map(this::toReceiptDetailResponse)
                        .collect(Collectors.toList());

        return new StockReceiptResponse(
                sr.getStockReceiptId(),
                sr.getReceiptNumber(),
                sr.getSupplier().getSupplierId(),
                sr.getSupplier().getSupplierName(),
                sr.getPurchaseOrder() != null ? sr.getPurchaseOrder().getOrderId() : null,
                sr.getPurchaseOrder() != null ? sr.getPurchaseOrder().getPoNumber() : null,
                sr.getCreatedBy().getUserId(),
                sr.getCreatedBy().getUsername(),
                sr.getTotalCost(),
                sr.getStatus(),
                sr.getReceivedAt(),
                sr.getCreatedAt(),
                detailItems
        );
    }

    private StockReceiptDetailResponse toReceiptDetailResponse(StockReceiptDetail d) {
        return new StockReceiptDetailResponse(
                d.getStockReceiptItemId(),
                d.getProduct().getProductId(),
                d.getProduct().getProductName(),
                d.getProduct().getProductCode(),
                d.getQuantity(),
                d.getRemainingQuantity(),
                d.getUnitCost(),
                d.getBatchNumber(),
                d.getExpirationDate(),
                d.getSubtotal()
        );
    }

    private StockMovementResponse toMovementResponse(StockMovement sm) {
        return new StockMovementResponse(
                sm.getMovementId(),
                sm.getProduct().getProductId(),
                sm.getProduct().getProductName(),
                sm.getProduct().getProductCode(),
                sm.getMovementType(),
                sm.getQuantityChange(),
                sm.getReferenceType(),
                sm.getReferenceId(),
                sm.getReason(),
                sm.getCreatedBy().getUserId(),
                sm.getCreatedBy().getUsername(),
                sm.getCreatedAt()
        );
    }

    private StockCheckResponse toCheckResponse(StockCheck sc) {
        return new StockCheckResponse(
                sc.getStockCheckId(),
                sc.getCheckNumber(),
                sc.getProduct().getProductId(),
                sc.getProduct().getProductName(),
                sc.getProduct().getProductCode(),
                sc.getCheckedBy().getUserId(),
                sc.getCheckedBy().getUsername(),
                sc.getSystemQuantity(),
                sc.getActualQuantity(),
                sc.getDifference(),
                sc.getReason(),
                sc.getStatus(),
                sc.getCheckedAt()
        );
    }
}