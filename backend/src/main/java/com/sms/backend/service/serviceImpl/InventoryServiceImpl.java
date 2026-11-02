package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.inventory.CreateStockReceiptItemRequest;
import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.dto.inventory.StockAlertFilter;
import com.sms.backend.dto.inventory.StockAlertItemResponse;
import com.sms.backend.dto.inventory.StockAlertSummaryResponse;
import com.sms.backend.dto.inventory.StockCheckItemRequest;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockCheckSessionRequest;
import com.sms.backend.dto.inventory.StockCheckSessionResponse;
import com.sms.backend.dto.inventory.UpdateStockCheckCountRequest;
import com.sms.backend.dto.inventory.StockHistoryFilter;
import com.sms.backend.dto.inventory.StockMovementPageResponse;
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
import com.sms.backend.repository.SystemSettingRepository;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    /**
     * Days-ahead window for NEAR_EXPIRY detection fallback.
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
    private final SystemSettingRepository systemSettingRepository;

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
    // Stock status derivation (SRS §3.10.1 & UC35)
    // =========================================================================

    private int getNearExpiryThresholdDays() {
        if (systemSettingRepository == null) return NEAR_EXPIRY_DAYS;
        return systemSettingRepository.findBySettingKey("NEAR_EXPIRY_DAYS")
                .or(() -> systemSettingRepository.findBySettingKey("EXPIRY_WARNING_DAYS"))
                .or(() -> systemSettingRepository.findBySettingKey("NEAR_EXPIRY_WARNING_DAYS"))
                .or(() -> systemSettingRepository.findBySettingKey("EXPIRATION_WARNING_DAYS"))
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getSettingValue().trim());
                    } catch (Exception e) {
                        return NEAR_EXPIRY_DAYS;
                    }
                })
                .orElse(NEAR_EXPIRY_DAYS);
    }

    private BigDecimal getDefaultLowStockThreshold() {
        if (systemSettingRepository == null) return BigDecimal.ZERO;
        return systemSettingRepository.findBySettingKey("DEFAULT_LOW_STOCK_THRESHOLD")
                .or(() -> systemSettingRepository.findBySettingKey("LOW_STOCK_THRESHOLD"))
                .map(s -> {
                    try {
                        return new BigDecimal(s.getSettingValue().trim());
                    } catch (Exception e) {
                        return BigDecimal.ZERO;
                    }
                })
                .orElse(BigDecimal.ZERO);
    }

    private String deriveStockStatus(Product p) {
        BigDecimal qty = p.getStockQuantity();
        BigDecimal minLevel = p.getMinimumStockLevel();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return "OUT_OF_STOCK";
        if (Boolean.TRUE.equals(p.getTrackExpiry())) {
            LocalDate threshold = LocalDate.now().plusDays(getNearExpiryThresholdDays());
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
    // UC33 - Adjust Stock
    // =========================================================================

    @Override
    @Transactional
    public StockAdjustmentResponse adjustStock(StockAdjustmentRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        if (request.productId() == null) {
            throw new BadRequestException("Product ID is required");
        }

        // 1. Validate Adjustment Type
        String typeStr = request.adjustmentType() != null ? request.adjustmentType().trim().toUpperCase() : "";
        if (!"INCREASE".equals(typeStr) && !"DECREASE".equals(typeStr)) {
            throw new BadRequestException("Invalid adjustment type: " + request.adjustmentType() + ". Must be INCREASE or DECREASE.");
        }

        // 2. Validate Adjustment Quantity (Positive Integer)
        BigDecimal qty = request.adjustmentQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Adjustment quantity must be greater than 0");
        }
        if (qty.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("Adjustment quantity must be a positive integer");
        }

        // 3. Validate Reason (Mandatory, max 255 chars)
        String reason = request.reason() != null ? request.reason().trim() : "";
        if (reason.isEmpty()) {
            throw new BadRequestException("Adjustment reason is mandatory");
        }
        if (reason.length() > 255) {
            throw new BadRequestException("Adjustment reason must not exceed 255 characters");
        }

        // 4. Validate Stock Check reference if provided (prevent duplicate update for same discrepancy)
        StockCheck linkedStockCheck = null;
        if (request.stockCheckId() != null) {
            if (stockMovementRepository.existsByReferenceTypeAndReferenceId(StockReferenceType.STOCK_CHECK, request.stockCheckId())) {
                throw new BusinessException("Stock adjustment has already been applied for stock check item: " + request.stockCheckId());
            }
            StockCheck sc = stockCheckRepository.findById(request.stockCheckId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stock check record not found with ID: " + request.stockCheckId()));

            if (!sc.getProduct().getProductId().equals(request.productId())) {
                throw new BadRequestException("Stock check session does not match the specified product");
            }
            if ("COMPLETED".equalsIgnoreCase(sc.getStatus()) || "ADJUSTED".equalsIgnoreCase(sc.getStatus())) {
                throw new BusinessException("Stock adjustment has already been applied for this stock check item");
            }

            // Verify discrepancy exists and matches requested adjustment type and quantity
            BigDecimal diff = sc.getDifference() != null ? sc.getDifference() : BigDecimal.ZERO;
            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                throw new BadRequestException("Cannot adjust stock for a balanced stock check record (variance is 0)");
            }
            String expectedType = diff.compareTo(BigDecimal.ZERO) < 0 ? "DECREASE" : "INCREASE";
            if (!expectedType.equalsIgnoreCase(typeStr)) {
                throw new BadRequestException("Adjustment type must be " + expectedType + " for this stock check discrepancy");
            }
            BigDecimal expectedQty = diff.abs();
            if (qty.compareTo(expectedQty) != 0) {
                throw new BadRequestException("Adjustment quantity (" + qty + ") does not match audited discrepancy (" + expectedQty + ")");
            }

            linkedStockCheck = sc;
        }

        // 5. Lock product and fetch current stock (pessimistic write lock for concurrency safety)
        Product product = productRepository.findByIdWithLock(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.productId()));

        if (product.getStatus() != GeneralStatus.ACTIVE) {
            throw new BadRequestException("Cannot adjust stock for inactive product: " + product.getProductName());
        }

        BigDecimal previousStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;

        // Conflict detection: If current stock has changed since counting, do not blindly apply an outdated variance
        if (linkedStockCheck != null && linkedStockCheck.getSystemQuantity() != null) {
            if (previousStock.compareTo(linkedStockCheck.getSystemQuantity()) != 0) {
                throw new BusinessException("Stock conflict: Current stock (" + previousStock
                        + ") has changed from counted system balance (" + linkedStockCheck.getSystemQuantity()
                        + "). Master stock has moved since the audit. Please re-verify the count before adjusting.");
            }
            linkedStockCheck.setStatus("COMPLETED");
            stockCheckRepository.save(linkedStockCheck);
        }
        BigDecimal newStock;
        BigDecimal quantityChange;

        if ("INCREASE".equals(typeStr)) {
            newStock = previousStock.add(qty);
            quantityChange = qty;
        } else {
            // DECREASE: prevent negative stock
            if (previousStock.compareTo(qty) < 0) {
                throw new BadRequestException("Insufficient stock. Resulting stock cannot be negative (current stock: "
                        + previousStock + ", requested decrease: " + qty + ")");
            }
            newStock = previousStock.subtract(qty);
            quantityChange = qty.negate();
        }

        // 6. Update Product stock
        product.setStockQuantity(newStock);
        productRepository.save(product);

        // 7. Record StockMovement
        User currentUser = resolveCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType(StockMovementType.ADJUSTMENT);
        movement.setQuantityChange(quantityChange);
        movement.setReferenceType(StockReferenceType.STOCK_CHECK);
        movement.setReferenceId(request.stockCheckId() != null ? request.stockCheckId() : 0L);
        movement.setReason(reason);
        movement.setCreatedBy(currentUser);
        movement.setCreatedAt(now);

        StockMovement savedMovement = stockMovementRepository.save(movement);

        return new StockAdjustmentResponse(
                savedMovement.getMovementId(),
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getUnit(),
                typeStr,
                previousStock,
                qty,
                newStock,
                reason,
                request.stockCheckId(),
                currentUser.getUsername(),
                now
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
        if (receipt.getStatus() != StockReceiptStatus.DRAFT) {
            throw new BusinessException("Only DRAFT receipts can be confirmed");
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
    @Transactional
    public StockReceiptResponse updateStockReceipt(Long id, CreateStockReceiptRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        StockReceipt receipt = findReceiptById(id);

        if (receipt.getStatus() == StockReceiptStatus.CONFIRMED) {
            throw new BusinessException("Cannot edit confirmed stock receipt #" + receipt.getReceiptNumber());
        }
        if (receipt.getStatus() == StockReceiptStatus.CANCELLED) {
            throw new BusinessException("Cannot edit cancelled stock receipt #" + receipt.getReceiptNumber());
        }
        if (receipt.getStatus() != StockReceiptStatus.DRAFT) {
            throw new BusinessException("Cannot edit receipt with status " + receipt.getStatus() + ". Only DRAFT receipts can be edited.");
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

        // Target status: DRAFT or CONFIRMED
        StockReceiptStatus targetStatus = request.status() != null ? request.status() : StockReceiptStatus.DRAFT;
        if (targetStatus == StockReceiptStatus.CANCELLED) {
            throw new BadRequestException("Use cancel endpoint to cancel a stock receipt");
        }

        // 3. Pre-validate products & calculate subtotals
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

        // 4. Update header (preserve original receiptNumber, createdAt, createdBy, and DRAFT status initially)
        receipt.setSupplier(supplier);
        receipt.setPurchaseOrder(purchaseOrder);
        receipt.setTotalCost(totalCost);
        receipt = stockReceiptRepository.save(receipt);

        // 5. Remove existing details for this draft receipt
        List<StockReceiptDetail> existingDetails = stockReceiptDetailRepository.findByStockReceipt(receipt);
        stockReceiptDetailRepository.deleteAll(existingDetails);
        stockReceiptDetailRepository.flush();

        // 6. Save new details
        for (ItemValidationContext item : validatedItems) {
            StockReceiptDetail detail = new StockReceiptDetail();
            detail.setStockReceipt(receipt);
            detail.setProduct(item.product);
            detail.setQuantity(item.quantity);
            detail.setRemainingQuantity(BigDecimal.ZERO);
            detail.setUnitCost(item.unitCost);
            detail.setBatchNumber(item.batchNumber);
            detail.setExpirationDate(item.expirationDate);
            detail.setSubtotal(item.subtotal);
            stockReceiptDetailRepository.save(detail);
        }

        // 7. If target status is CONFIRMED, confirm immediately
        if (targetStatus == StockReceiptStatus.CONFIRMED) {
            return confirmStockReceipt(receipt.getStockReceiptId());
        }

        return getStockReceiptById(receipt.getStockReceiptId());
    }

    @Override
    @Transactional
    public StockReceiptResponse cancelStockReceipt(Long id) {
        StockReceipt receipt = findReceiptById(id);

        if (receipt.getStatus() == StockReceiptStatus.CANCELLED) {
            throw new BusinessException("Stock receipt is already cancelled");
        }
        if (receipt.getStatus() == StockReceiptStatus.CONFIRMED) {
            throw new BusinessException("Cannot cancel a confirmed stock receipt");
        }
        if (receipt.getStatus() != StockReceiptStatus.DRAFT) {
            throw new BusinessException("Only DRAFT receipts can be cancelled");
        }

        receipt.setStatus(StockReceiptStatus.CANCELLED);
        receipt = stockReceiptRepository.save(receipt);

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
    // UC34 - Stock History
    // =========================================================================

    @Override
    public StockMovementPageResponse getStockHistory(StockHistoryFilter filter) {
        if (filter.startDate() != null && filter.endDate() != null && filter.startDate().isAfter(filter.endDate())) {
            throw new BadRequestException("Start date cannot be after end date");
        }

        LocalDateTime dateFrom = filter.startDate() != null ? filter.startDate().atStartOfDay() : null;
        LocalDateTime dateTo = filter.endDate() != null ? filter.endDate().atTime(LocalTime.MAX) : null;

        String keyword = filter.keyword() != null && !filter.keyword().isBlank()
                ? filter.keyword().trim()
                : null;

        PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());

        Page<StockMovement> page = stockMovementRepository.findAllByFilter(
                filter.productId(),
                keyword,
                filter.movementType(),
                dateFrom,
                dateTo,
                pageRequest
        );

        List<StockMovementResponse> content = page.getContent().stream()
                .map(this::toMovementResponse)
                .toList();

        return new StockMovementPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

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
        String referenceCode;
        if (sm.getReferenceType() == StockReferenceType.STOCK_RECEIPT) {
            String receiptNum = null;
            try {
                if (sm.getStockReceiptDetail() != null && sm.getStockReceiptDetail().getStockReceipt() != null) {
                    receiptNum = sm.getStockReceiptDetail().getStockReceipt().getReceiptNumber();
                }
            } catch (Exception ignored) {}
            referenceCode = receiptNum != null ? receiptNum : ("SR#" + sm.getReferenceId());
        } else if (sm.getReferenceType() == StockReferenceType.SALE_ITEM) {
            referenceCode = "SALE#" + sm.getReferenceId();
        } else if (sm.getReferenceType() == StockReferenceType.STOCK_CHECK) {
            if (sm.getReferenceId() != null && sm.getReferenceId() > 0) {
                referenceCode = "SC#" + sm.getReferenceId();
            } else {
                referenceCode = "ADJ#" + sm.getMovementId();
            }
        } else {
            referenceCode = sm.getReferenceType() != null
                    ? (sm.getReferenceType() + "#" + sm.getReferenceId())
                    : ("#" + sm.getReferenceId());
        }

        Product p = sm.getProduct();
        User u = sm.getCreatedBy();

        return new StockMovementResponse(
                sm.getMovementId(),
                p != null ? p.getProductId() : null,
                p != null ? p.getProductName() : null,
                p != null ? p.getProductCode() : null,
                p != null ? p.getBarcode() : null,
                p != null ? p.getUnit() : null,
                p != null ? p.getStockQuantity() : null,
                sm.getMovementType(),
                sm.getQuantityChange(),
                sm.getReferenceType(),
                sm.getReferenceId(),
                referenceCode,
                sm.getReason(),
                u != null ? u.getUserId() : null,
                u != null ? u.getUsername() : null,
                sm.getCreatedAt()
        );
    }

    private StockCheckResponse toCheckResponse(StockCheck sc) {
        Product p = sc.getProduct();
        BigDecimal price = (p != null && p.getSellingPrice() != null) ? p.getSellingPrice() : BigDecimal.ZERO;
        BigDecimal financialImpact = (sc.getDifference() != null && price != null)
                ? sc.getDifference().multiply(price)
                : BigDecimal.ZERO;

        BigDecimal currentStock = (p != null && p.getStockQuantity() != null) ? p.getStockQuantity() : BigDecimal.ZERO;
        boolean isReconciled = "COMPLETED".equalsIgnoreCase(sc.getStatus()) || "ADJUSTED".equalsIgnoreCase(sc.getStatus());
        boolean hasDiscrepancy = sc.getDifference() != null && sc.getDifference().compareTo(BigDecimal.ZERO) != 0;
        boolean isStale = !isReconciled && hasDiscrepancy && sc.getSystemQuantity() != null && currentStock.compareTo(sc.getSystemQuantity()) != 0;

        return new StockCheckResponse(
                sc.getStockCheckId(),
                sc.getCheckNumber(),
                p != null ? p.getProductId() : null,
                p != null ? p.getProductName() : null,
                p != null ? p.getProductCode() : null,
                p != null ? p.getBarcode() : null,
                (p != null && p.getCategory() != null) ? p.getCategory().getCategoryName() : "Uncategorized",
                p != null ? p.getUnit() : null,
                sc.getCheckedBy() != null ? sc.getCheckedBy().getUserId() : null,
                sc.getCheckedBy() != null ? sc.getCheckedBy().getUsername() : null,
                sc.getSystemQuantity(),
                sc.getActualQuantity(),
                sc.getDifference(),
                currentStock,
                isStale,
                price,
                financialImpact,
                sc.getReason(),
                sc.getStatus(),
                sc.getCheckedAt()
        );
    }

    // =========================================================================
    // UC32 - Stock Check & Stock Difference
    // =========================================================================

    @Override
    public String getNextStockCheckSessionNumber() {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long nextId = stockCheckRepository.findTopByOrderByStockCheckIdDesc()
                .map(sc -> sc.getStockCheckId() + 1)
                .orElse(1L);
        String candidate = String.format("SC-%s-%03d", todayStr, nextId % 1000 == 0 ? 1 : nextId % 1000);
        int counter = 1;
        while (stockCheckRepository.existsByCheckNumber(candidate)) {
            candidate = String.format("SC-%s-%03d", todayStr, counter++);
        }
        return candidate;
    }

    @Override
    @Transactional
    public StockCheckSessionResponse performStockCheck(StockCheckSessionRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("At least one product item must be counted in the stock check session");
        }

        String sessionNumber = request.checkNumber() != null && !request.checkNumber().isBlank()
                ? request.checkNumber().trim()
                : getNextStockCheckSessionNumber();

        // Prevent duplicate products in the same audit session (BR-04)
        Set<Long> seenProductIds = new HashSet<>();
        for (StockCheckItemRequest item : request.items()) {
            if (item.productId() == null) {
                throw new BadRequestException("Product ID is required for all counted items");
            }
            if (!seenProductIds.add(item.productId())) {
                throw new BadRequestException("An item can only appear once per audit session. Duplicate product ID: " + item.productId());
            }
            if (item.actualQuantity() == null || item.actualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Physical Count values must be non-negative integers (>= 0)");
            }
            if (item.actualQuantity().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new BadRequestException("Physical Count values must be non-negative integers");
            }
        }

        User currentUser = resolveCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        List<StockCheck> savedChecks = new ArrayList<>();

        for (StockCheckItemRequest item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + item.productId()));

            if (product.getStatus() != GeneralStatus.ACTIVE) {
                throw new BadRequestException("Cannot perform stock check on inactive product: " + product.getProductName());
            }

            BigDecimal systemQty = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal actualQty = item.actualQuantity();
            BigDecimal diff = actualQty.subtract(systemQty);
            String status = diff.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "PENDING_ADJUSTMENT";

            StockCheck sc = new StockCheck();
            sc.setCheckNumber(sessionNumber);
            sc.setProduct(product);
            sc.setCheckedBy(currentUser);
            sc.setSystemQuantity(systemQty);
            sc.setActualQuantity(actualQty);
            sc.setDifference(diff);
            sc.setReason(item.reason() != null && !item.reason().isBlank() ? item.reason().trim() : null);
            sc.setStatus(status);
            sc.setCheckedAt(now);

            // Note: UC32 only records physical counts and calculates discrepancies.
            // Master Product.stockQuantity is NOT altered, and StockMovement is NOT created here (BR-05).
            savedChecks.add(stockCheckRepository.save(sc));
        }

        return buildSessionResponse(sessionNumber, currentUser != null ? currentUser.getUsername() : "System", now, savedChecks);
    }

    @Override
    public StockCheckSessionResponse getStockCheckSession(String checkNumber) {
        if (checkNumber == null || checkNumber.isBlank()) {
            throw new BadRequestException("Session check number is required");
        }
        List<StockCheck> list = stockCheckRepository.findByCheckNumber(checkNumber.trim());
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Stock check session not found: " + checkNumber);
        }

        StockCheck first = list.get(0);
        String username = first.getCheckedBy() != null ? first.getCheckedBy().getUsername() : "System";
        LocalDateTime checkedAt = first.getCheckedAt() != null ? first.getCheckedAt() : LocalDateTime.now();

        return buildSessionResponse(checkNumber.trim(), username, checkedAt, list);
    }

    @Override
    public List<StockCheckSessionResponse> getAllStockCheckSessions() {
        List<StockCheck> all = stockCheckRepository.findAllWithDetails();
        java.util.Map<String, List<StockCheck>> grouped = all.stream()
                .collect(Collectors.groupingBy(StockCheck::getCheckNumber, java.util.LinkedHashMap::new, Collectors.toList()));

        List<StockCheckSessionResponse> result = new ArrayList<>();
        for (java.util.Map.Entry<String, List<StockCheck>> entry : grouped.entrySet()) {
            String checkNumber = entry.getKey();
            List<StockCheck> checks = entry.getValue();
            StockCheck first = checks.get(0);
            String username = first.getCheckedBy() != null ? first.getCheckedBy().getUsername() : "System";
            LocalDateTime checkedAt = first.getCheckedAt() != null ? first.getCheckedAt() : LocalDateTime.now();
            result.add(buildSessionResponse(checkNumber, username, checkedAt, checks));
        }
        return result;
    }

    @Override
    @Transactional
    public StockCheckResponse updateStockCheckItemCount(Long stockCheckId, UpdateStockCheckCountRequest request) {
        if (stockCheckId == null) {
            throw new BadRequestException("Stock check ID is required");
        }
        if (request == null || request.actualQuantity() == null) {
            throw new BadRequestException("Physical count quantity is required");
        }
        if (request.actualQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Physical count must be non-negative (>= 0)");
        }
        if (request.actualQuantity().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("Physical count must be a whole integer");
        }

        StockCheck sc = stockCheckRepository.findById(stockCheckId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock check record not found with ID: " + stockCheckId));

        // Reject if item has already been reconciled/adjusted through UC33
        if ("COMPLETED".equalsIgnoreCase(sc.getStatus()) || "ADJUSTED".equalsIgnoreCase(sc.getStatus())
                || stockMovementRepository.existsByReferenceTypeAndReferenceId(StockReferenceType.STOCK_CHECK, stockCheckId)) {
            throw new BusinessException("Cannot edit physical count for an item that has already been reconciled/adjusted through UC33");
        }

        BigDecimal systemQty = sc.getSystemQuantity() != null ? sc.getSystemQuantity() : BigDecimal.ZERO;
        BigDecimal newActualQty = request.actualQuantity();
        BigDecimal newDiff = newActualQty.subtract(systemQty);
        String newStatus = newDiff.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "PENDING_ADJUSTMENT";

        sc.setActualQuantity(newActualQty);
        sc.setDifference(newDiff);
        sc.setStatus(newStatus);
        if (request.reason() != null && !request.reason().isBlank()) {
            sc.setReason(request.reason().trim());
        }

        // Master Product.stockQuantity remains untouched and no StockMovement is created
        StockCheck saved = stockCheckRepository.save(sc);
        return toCheckResponse(saved);
    }

    private StockCheckSessionResponse buildSessionResponse(String checkNumber, String username, LocalDateTime checkedAt, List<StockCheck> checks) {
        int totalSkus = checks.size();
        int matchedCount = 0;
        int discrepancyCount = 0;
        int staleCount = 0;
        BigDecimal netQtyVariance = BigDecimal.ZERO;
        BigDecimal netFinancialVariance = BigDecimal.ZERO;
        List<StockCheckResponse> itemResponses = new ArrayList<>();

        for (StockCheck sc : checks) {
            StockCheckResponse resp = toCheckResponse(sc);
            itemResponses.add(resp);

            BigDecimal diff = sc.getDifference() != null ? sc.getDifference() : BigDecimal.ZERO;
            netQtyVariance = netQtyVariance.add(diff);
            if (resp.financialImpact() != null) {
                netFinancialVariance = netFinancialVariance.add(resp.financialImpact());
            }

            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                matchedCount++;
            } else {
                discrepancyCount++;
            }

            if (Boolean.TRUE.equals(resp.isStale())) {
                staleCount++;
            }
        }

        return new StockCheckSessionResponse(
                checkNumber,
                username,
                checkedAt,
                totalSkus,
                matchedCount,
                discrepancyCount,
                staleCount,
                netQtyVariance,
                netFinancialVariance,
                itemResponses
        );
    }

    // =========================================================================
    // UC35 - Stock Alerts
    // =========================================================================

    @Override
    public StockAlertSummaryResponse getStockAlerts(StockAlertFilter filter) {
        LocalDate today = LocalDate.now();
        int warningDays = getNearExpiryThresholdDays();
        LocalDate nearExpiryThreshold = today.plusDays(warningDays);

        List<StockAlertItemResponse> allAlerts = new ArrayList<>();

        // 1. Evaluate Product stock level alerts (OUT_OF_STOCK, LOW_STOCK)
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            if (p.getStatus() != GeneralStatus.ACTIVE) continue;

            BigDecimal stock = p.getStockQuantity() == null ? BigDecimal.ZERO : p.getStockQuantity();
            BigDecimal minLevel = p.getMinimumStockLevel();
            if (minLevel == null || minLevel.compareTo(BigDecimal.ZERO) <= 0) {
                minLevel = getDefaultLowStockThreshold();
            }

            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                allAlerts.add(new StockAlertItemResponse(
                        "STOCK-" + p.getProductId() + "-OUT_OF_STOCK",
                        p.getProductId(),
                        p.getProductCode(),
                        p.getBarcode(),
                        p.getProductName(),
                        p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                        p.getCategory() != null ? p.getCategory().getCategoryName() : "Uncategorized",
                        p.getUnit(),
                        "OUT_OF_STOCK",
                        stock,
                        minLevel,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else if (minLevel.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(minLevel) <= 0) {
                allAlerts.add(new StockAlertItemResponse(
                        "STOCK-" + p.getProductId() + "-LOW_STOCK",
                        p.getProductId(),
                        p.getProductCode(),
                        p.getBarcode(),
                        p.getProductName(),
                        p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                        p.getCategory() != null ? p.getCategory().getCategoryName() : "Uncategorized",
                        p.getUnit(),
                        "LOW_STOCK",
                        stock,
                        minLevel,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        // 2. Evaluate batch expiration alerts (EXPIRED, NEAR_EXPIRY)
        // Confirmed receipts only, remaining quantity > 0, trackExpiry = true
        List<StockReceiptDetail> expiringBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        for (StockReceiptDetail d : expiringBatches) {
            Product p = d.getProduct();
            if (p == null || p.getStatus() != GeneralStatus.ACTIVE) continue;
            LocalDate exp = d.getExpirationDate();
            if (exp == null) continue;

            long days = ChronoUnit.DAYS.between(today, exp);

            if (exp.isBefore(today)) {
                allAlerts.add(new StockAlertItemResponse(
                        "BATCH-" + d.getStockReceiptItemId() + "-EXPIRED",
                        p.getProductId(),
                        p.getProductCode(),
                        p.getBarcode(),
                        p.getProductName(),
                        p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                        p.getCategory() != null ? p.getCategory().getCategoryName() : "Uncategorized",
                        p.getUnit(),
                        "EXPIRED",
                        p.getStockQuantity(),
                        p.getMinimumStockLevel(),
                        d.getBatchNumber(),
                        d.getRemainingQuantity(),
                        exp,
                        days,
                        d.getStockReceipt() != null ? d.getStockReceipt().getReceiptNumber() : null
                ));
            } else if (!exp.isAfter(nearExpiryThreshold)) {
                allAlerts.add(new StockAlertItemResponse(
                        "BATCH-" + d.getStockReceiptItemId() + "-NEAR_EXPIRY",
                        p.getProductId(),
                        p.getProductCode(),
                        p.getBarcode(),
                        p.getProductName(),
                        p.getCategory() != null ? p.getCategory().getCategoryId() : null,
                        p.getCategory() != null ? p.getCategory().getCategoryName() : "Uncategorized",
                        p.getUnit(),
                        "NEAR_EXPIRY",
                        p.getStockQuantity(),
                        p.getMinimumStockLevel(),
                        d.getBatchNumber(),
                        d.getRemainingQuantity(),
                        exp,
                        days,
                        d.getStockReceipt() != null ? d.getStockReceipt().getReceiptNumber() : null
                ));
            }
        }

        // 3. Category filtering for counts and items
        Long catId = filter.categoryId();
        List<StockAlertItemResponse> categoryAlerts = catId != null
                ? allAlerts.stream().filter(a -> catId.equals(a.categoryId())).toList()
                : allAlerts;

        long lowStockCount = categoryAlerts.stream().filter(a -> "LOW_STOCK".equalsIgnoreCase(a.alertType())).count();
        long outOfStockCount = categoryAlerts.stream().filter(a -> "OUT_OF_STOCK".equalsIgnoreCase(a.alertType())).count();
        long nearExpiryCount = categoryAlerts.stream().filter(a -> "NEAR_EXPIRY".equalsIgnoreCase(a.alertType())).count();
        long expiredCount = categoryAlerts.stream().filter(a -> "EXPIRED".equalsIgnoreCase(a.alertType())).count();
        long totalAlerts = categoryAlerts.size();

        // 4. Apply Alert Type and Keyword Filters
        String kw = filter.normalizedKeyword();
        String kwLower = kw != null ? kw.toLowerCase() : null;
        String alertType = filter.alertType();

        List<StockAlertItemResponse> filtered = categoryAlerts.stream()
                .filter(a -> {
                    if (alertType != null && !alertType.isBlank() && !"ALL".equalsIgnoreCase(alertType)) {
                        if (!alertType.equalsIgnoreCase(a.alertType())) {
                            return false;
                        }
                    }
                    if (kwLower != null) {
                        boolean matchName = a.productName() != null && a.productName().toLowerCase().contains(kwLower);
                        boolean matchCode = a.productCode() != null && a.productCode().toLowerCase().contains(kwLower);
                        boolean matchBarcode = a.barcode() != null && a.barcode().toLowerCase().contains(kwLower);
                        if (!matchName && !matchCode && !matchBarcode) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparing((StockAlertItemResponse a) -> getAlertSeverityOrder(a.alertType()))
                        .thenComparing(a -> a.expiryDate() != null ? a.expiryDate() : LocalDate.MAX)
                        .thenComparing(a -> a.productName() != null ? a.productName() : "")
                )
                .collect(Collectors.toList());

        long totalElements = filtered.size();
        int size = filter.size();
        int page = filter.page();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int fromIndex = Math.min(page * size, (int) totalElements);
        int toIndex = Math.min(fromIndex + size, (int) totalElements);
        List<StockAlertItemResponse> pageAlerts = filtered.subList(fromIndex, toIndex);

        return new StockAlertSummaryResponse(
                lowStockCount,
                outOfStockCount,
                nearExpiryCount,
                expiredCount,
                totalAlerts,
                pageAlerts,
                page,
                size,
                totalElements,
                totalPages
        );
    }

    private int getAlertSeverityOrder(String alertType) {
        if ("EXPIRED".equalsIgnoreCase(alertType)) return 1;
        if ("OUT_OF_STOCK".equalsIgnoreCase(alertType)) return 2;
        if ("NEAR_EXPIRY".equalsIgnoreCase(alertType)) return 3;
        if ("LOW_STOCK".equalsIgnoreCase(alertType)) return 4;
        return 5;
    }
}