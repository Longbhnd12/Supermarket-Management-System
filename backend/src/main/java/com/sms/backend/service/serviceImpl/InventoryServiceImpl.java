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
import com.sms.backend.dto.inventory.StockCheckSessionPageResponse;
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
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.StockCheckRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.InventoryService;
import com.sms.backend.service.NotificationService;
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
import java.util.HashMap;
import java.util.Objects;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final PurchaseOrderDetailRepository purchaseOrderDetailRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final NotificationService notificationService;

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

    /**
     * UC31: Stock status is QUANTITY-LEVEL only — independent of batch expiry.
     * IN_STOCK / LOW_STOCK / OUT_OF_STOCK solely based on stockQuantity vs minimumStockLevel.
     * Batch expiry conditions are reported separately via batchExpiryAlerts.
     */
    private String deriveStockStatus(Product p) {
        BigDecimal qty = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal minLevel = p.getMinimumStockLevel() != null ? p.getMinimumStockLevel() : BigDecimal.ZERO;
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return "OUT_OF_STOCK";
        if (minLevel.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(minLevel) <= 0) return "LOW_STOCK";
        return "IN_STOCK";
    }

    /**
     * UC31: Batch expiry alerts for a product — one entry per CONFIRMED batch that is
     * near-expiry or already expired, with remainingQuantity > 0 and trackExpiry=true.
     */
    private List<InventoryResponse.BatchExpiryAlertInfo> deriveBatchExpiryAlerts(Product p) {
        if (!Boolean.TRUE.equals(p.getTrackExpiry())) return List.of();
        LocalDate today = LocalDate.now();
        LocalDate nearExpiryThreshold = today.plusDays(getNearExpiryThresholdDays());

        return stockReceiptDetailRepository.findByProduct(p).stream()
                .filter(d -> d.getStockReceipt().getStatus() == StockReceiptStatus.CONFIRMED)
                .filter(d -> d.getRemainingQuantity() != null
                          && d.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                .filter(d -> d.getExpirationDate() != null
                          && !d.getExpirationDate().isAfter(nearExpiryThreshold))
                .map(d -> {
                    LocalDate exp = d.getExpirationDate();
                    long days = java.time.temporal.ChronoUnit.DAYS.between(today, exp);
                    String alertType = exp.isBefore(today) ? "EXPIRED" : "NEAR_EXPIRY";
                    String receiptNum = d.getStockReceipt() != null
                            ? d.getStockReceipt().getReceiptNumber() : null;
                    return new InventoryResponse.BatchExpiryAlertInfo(
                            d.getStockReceiptItemId(),
                            d.getBatchNumber(),
                            receiptNum,
                            d.getRemainingQuantity(),
                            exp,
                            days,
                            alertType
                    );
                })
                .sorted(Comparator.comparing(InventoryResponse.BatchExpiryAlertInfo::expirationDate))
                .collect(Collectors.toList());
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
                deriveStockStatus(p),
                deriveBatchExpiryAlerts(p)
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
    public List<com.sms.backend.dto.inventory.ProductBatchResponse> getProductBatches(Long productId) {
        if (productId == null) {
            throw new BadRequestException("Product ID is required");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        LocalDate today = LocalDate.now();

        return stockReceiptDetailRepository.findByProduct(product).stream()
                .filter(d -> d.getStockReceipt() != null
                          && d.getStockReceipt().getStatus() == StockReceiptStatus.CONFIRMED)
                .sorted(Comparator
                        .comparing((StockReceiptDetail d) -> d.getExpirationDate() == null
                                ? LocalDate.MAX : d.getExpirationDate())
                        .thenComparing(d -> d.getStockReceipt().getReceivedAt() == null
                                ? LocalDateTime.MAX : d.getStockReceipt().getReceivedAt())
                        .thenComparing(StockReceiptDetail::getStockReceiptItemId))
                .map(d -> {
                    LocalDate exp = d.getExpirationDate();
                    boolean isExpired = exp != null && exp.isBefore(today);
                    String receiptNum = d.getStockReceipt() != null ? d.getStockReceipt().getReceiptNumber() : null;
                    Long receiptId = d.getStockReceipt() != null ? d.getStockReceipt().getStockReceiptId() : null;
                    LocalDateTime receivedAt = d.getStockReceipt() != null ? d.getStockReceipt().getReceivedAt() : null;

                    return new com.sms.backend.dto.inventory.ProductBatchResponse(
                            d.getStockReceiptItemId(),
                            product.getProductId(),
                            d.getBatchNumber(),
                            exp,
                            d.getRemainingQuantity() != null ? d.getRemainingQuantity() : BigDecimal.ZERO,
                            d.getQuantity(),
                            d.getUnitCost(),
                            receiptNum,
                            receiptId,
                            receivedAt,
                            isExpired
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<com.sms.backend.dto.inventory.GroupedProductBatchResponse> getGroupedProductBatches(Long productId) {
        if (productId == null) {
            throw new BadRequestException("Product ID is required");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        List<com.sms.backend.dto.inventory.ProductBatchResponse> detailResponses = getProductBatches(productId);
        LocalDate today = LocalDate.now();

        // Normalization helper (conservative, trimmed and uppercase)
        java.util.function.Function<String, String> normalizeBatch = s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT);

        // Record for grouping key: physical batch identity is (normalizedBatchNumber, expirationDate)
        record GroupKey(String normalizedBatch, LocalDate expirationDate) {}

        // Check for historical conflicting expiration dates for the same product and batchNumber
        Map<String, Set<LocalDate>> dateSetsByBatch = new HashMap<>();
        for (com.sms.backend.dto.inventory.ProductBatchResponse d : detailResponses) {
            String nb = normalizeBatch.apply(d.batchNumber());
            if (!nb.isEmpty()) {
                dateSetsByBatch.computeIfAbsent(nb, k -> new HashSet<>()).add(d.expirationDate());
            }
        }

        Map<GroupKey, List<com.sms.backend.dto.inventory.ProductBatchResponse>> groups = new LinkedHashMap<>();
        for (com.sms.backend.dto.inventory.ProductBatchResponse d : detailResponses) {
            GroupKey key = new GroupKey(normalizeBatch.apply(d.batchNumber()), d.expirationDate());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<com.sms.backend.dto.inventory.GroupedProductBatchResponse> result = new ArrayList<>();
        for (Map.Entry<GroupKey, List<com.sms.backend.dto.inventory.ProductBatchResponse>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<com.sms.backend.dto.inventory.ProductBatchResponse> list = entry.getValue();

            // Sort details within the group: earliest receivedAt first, then stockReceiptItemId
            list.sort(Comparator
                    .comparing((com.sms.backend.dto.inventory.ProductBatchResponse d) -> d.receivedAt() == null ? LocalDateTime.MAX : d.receivedAt())
                    .thenComparing(com.sms.backend.dto.inventory.ProductBatchResponse::stockReceiptItemId));

            String displayBatchNumber = list.getFirst().batchNumber();
            LocalDate expDate = key.expirationDate();
            BigDecimal totalRemaining = list.stream()
                    .map(d -> d.remainingQuantity() != null ? d.remainingQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalReceived = list.stream()
                    .map(d -> d.receivedQuantity() != null ? d.receivedQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean isExpired = expDate != null && expDate.isBefore(today);

            Set<LocalDate> dates = dateSetsByBatch.get(key.normalizedBatch());
            boolean hasConflictingDates = dates != null && dates.size() > 1;

            result.add(new com.sms.backend.dto.inventory.GroupedProductBatchResponse(
                    product.getProductId(),
                    displayBatchNumber,
                    expDate,
                    totalRemaining,
                    totalReceived,
                    list.size(),
                    isExpired,
                    hasConflictingDates,
                    list
            ));
        }

        // Sort grouped batches: earliest expiration date first (FEFO order), then batchNumber
        result.sort(Comparator
                .comparing((com.sms.backend.dto.inventory.GroupedProductBatchResponse g) -> g.expirationDate() == null ? LocalDate.MAX : g.expirationDate())
                .thenComparing(com.sms.backend.dto.inventory.GroupedProductBatchResponse::batchNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return result;
    }

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
            StockCheck sc = stockCheckRepository.findById(request.stockCheckId()).orElse(null);
            if (sc != null) {
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

        // Lock batches for this product
        List<StockReceiptDetail> lockedBatches = stockReceiptDetailRepository.lockBatches(product.getProductId());
        List<StockReceiptDetail> confirmedBatches = lockedBatches.stream()
                .filter(d -> d.getStockReceipt() != null && d.getStockReceipt().getStatus() == StockReceiptStatus.CONFIRMED)
                .toList();

        java.util.Map<Long, StockReceiptDetail> batchMap = confirmedBatches.stream()
                .collect(Collectors.toMap(StockReceiptDetail::getStockReceiptItemId, d -> d));

        List<StockAdjustmentRequest.BatchAllocationRequest> allocations = request.batchAllocations();
        List<StockAdjustmentRequest.GroupedBatchAllocationRequest> groupedAllocations = request.groupedBatchAllocations();
        List<java.util.Map.Entry<StockReceiptDetail, BigDecimal>> executedBatchAllocations = new ArrayList<>();

        if ("DECREASE".equals(typeStr) && groupedAllocations != null && !groupedAllocations.isEmpty()) {
            // Grouped batch allocation for DECREASE
            if (allocations != null && !allocations.isEmpty()) {
                throw new BadRequestException("Provide either grouped batch allocations or item allocations, not both.");
            }

            Set<String> seenGroupKeys = new HashSet<>();
            BigDecimal totalAllocated = BigDecimal.ZERO;
            java.util.function.Function<String, String> norm = s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT);

            for (StockAdjustmentRequest.GroupedBatchAllocationRequest groupAlloc : groupedAllocations) {
                if (groupAlloc.batchNumber() == null || groupAlloc.batchNumber().trim().isEmpty()) {
                    throw new BadRequestException("Batch number is required in grouped batch allocation");
                }
                String normBatch = norm.apply(groupAlloc.batchNumber());
                String groupKey = normBatch + "|" + (groupAlloc.expirationDate() != null ? groupAlloc.expirationDate() : "");
                if (!seenGroupKeys.add(groupKey)) {
                    throw new BadRequestException("Duplicate grouped batch allocation for batch: " + groupAlloc.batchNumber());
                }
                if (groupAlloc.quantity() == null || groupAlloc.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Allocated quantity must be greater than 0 for batch: " + groupAlloc.batchNumber());
                }
                if (groupAlloc.quantity().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                    throw new BadRequestException("Allocated quantity must be a whole integer for batch: " + groupAlloc.batchNumber());
                }

                // Find matching confirmed receipt details for this physical batch
                List<StockReceiptDetail> matchingDetails = confirmedBatches.stream()
                        .filter(d -> {
                            String dNorm = norm.apply(d.getBatchNumber());
                            if (!dNorm.equals(normBatch)) return false;
                            if (groupAlloc.expirationDate() == null) {
                                return d.getExpirationDate() == null;
                            }
                            return groupAlloc.expirationDate().equals(d.getExpirationDate());
                        })
                        .sorted(Comparator
                                .comparing((StockReceiptDetail d) -> d.getStockReceipt().getReceivedAt() == null ? LocalDateTime.MAX : d.getStockReceipt().getReceivedAt())
                                .thenComparing(StockReceiptDetail::getStockReceiptItemId))
                        .toList();

                if (matchingDetails.isEmpty()) {
                    throw new BadRequestException("Physical batch '" + groupAlloc.batchNumber() + "' not found or has no confirmed receipts for product " + product.getProductName());
                }

                BigDecimal totalAvailableInGroup = matchingDetails.stream()
                        .map(d -> d.getRemainingQuantity() != null ? d.getRemainingQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (groupAlloc.quantity().compareTo(totalAvailableInGroup) > 0) {
                    throw new BadRequestException("Insufficient remaining quantity in physical batch '" + groupAlloc.batchNumber()
                            + "'. Available: " + totalAvailableInGroup + ", requested decrease: " + groupAlloc.quantity());
                }

                // Deterministic allocation policy: earliest receipt date first within SAME physical batch, tie-break by stockReceiptItemId
                BigDecimal remainingToDeduct = groupAlloc.quantity();
                for (StockReceiptDetail detail : matchingDetails) {
                    if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal detailRemaining = detail.getRemainingQuantity() != null ? detail.getRemainingQuantity() : BigDecimal.ZERO;
                    if (detailRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal deduct = detailRemaining.min(remainingToDeduct);
                    detail.setRemainingQuantity(detailRemaining.subtract(deduct));
                    remainingToDeduct = remainingToDeduct.subtract(deduct);

                    stockReceiptDetailRepository.save(detail);
                    executedBatchAllocations.add(java.util.Map.entry(detail, deduct));
                }

                if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
                    throw new BadRequestException("Failed to fully allocate decrease of " + groupAlloc.quantity() + " for batch " + groupAlloc.batchNumber());
                }

                totalAllocated = totalAllocated.add(groupAlloc.quantity());
            }

            if (totalAllocated.compareTo(qty) != 0) {
                throw new BadRequestException("Sum of allocated batch quantities (" + totalAllocated
                        + ") must equal total adjustment quantity (" + qty + ")");
            }
        } else if (allocations != null && !allocations.isEmpty()) {
            if ("INCREASE".equals(typeStr) && groupedAllocations != null && !groupedAllocations.isEmpty()) {
                throw new BadRequestException("Grouped allocation cannot be used for stock increase. Specific receipt detail must be selected.");
            }

            // Validate unique batches in allocation request
            Set<Long> seenBatchIds = new HashSet<>();
            BigDecimal totalAllocated = BigDecimal.ZERO;

            for (StockAdjustmentRequest.BatchAllocationRequest alloc : allocations) {
                if (alloc.stockReceiptItemId() == null) {
                    throw new BadRequestException("Batch item ID is required in batch allocation");
                }
                if (!seenBatchIds.add(alloc.stockReceiptItemId())) {
                    throw new BadRequestException("Duplicate batch allocation for batch ID: " + alloc.stockReceiptItemId());
                }
                if (alloc.quantity() == null || alloc.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Allocated quantity must be greater than 0 for batch ID: " + alloc.stockReceiptItemId());
                }
                if (alloc.quantity().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                    throw new BadRequestException("Allocated quantity must be a whole integer for batch ID: " + alloc.stockReceiptItemId());
                }

                StockReceiptDetail batch = batchMap.get(alloc.stockReceiptItemId());
                if (batch == null) {
                    throw new BadRequestException("Batch record #" + alloc.stockReceiptItemId()
                            + " not found or does not belong to confirmed receipt for product " + product.getProductName());
                }

                // Verify batch number and expiration date consistency if provided in the allocation request
                if (alloc.batchNumber() != null && !alloc.batchNumber().trim().isEmpty()) {
                    String reqBatch = alloc.batchNumber().trim().toUpperCase(Locale.ROOT);
                    String actualBatch = batch.getBatchNumber() != null ? batch.getBatchNumber().trim().toUpperCase(Locale.ROOT) : "";
                    if (!reqBatch.equals(actualBatch)) {
                        throw new BadRequestException("Batch number mismatch for receipt detail #" + alloc.stockReceiptItemId()
                                + ". Expected: " + actualBatch + ", requested: " + alloc.batchNumber());
                    }
                }
                if (alloc.expirationDate() != null) {
                    if (batch.getExpirationDate() == null || !alloc.expirationDate().equals(batch.getExpirationDate())) {
                        throw new BadRequestException("Expiration date mismatch for receipt detail #" + alloc.stockReceiptItemId()
                                + ". Expected: " + batch.getExpirationDate() + ", requested: " + alloc.expirationDate());
                    }
                }

                if ("DECREASE".equals(typeStr)) {
                    BigDecimal currentRemaining = batch.getRemainingQuantity() != null ? batch.getRemainingQuantity() : BigDecimal.ZERO;
                    if (alloc.quantity().compareTo(currentRemaining) > 0) {
                        throw new BadRequestException("Insufficient remaining quantity in batch "
                                + (batch.getBatchNumber() != null ? batch.getBatchNumber() : ("#" + batch.getStockReceiptItemId()))
                                + ". Available: " + currentRemaining + ", requested decrease: " + alloc.quantity());
                    }
                }

                totalAllocated = totalAllocated.add(alloc.quantity());
                executedBatchAllocations.add(java.util.Map.entry(batch, alloc.quantity()));
            }

            if (totalAllocated.compareTo(qty) != 0) {
                throw new BadRequestException("Sum of allocated batch quantities (" + totalAllocated
                        + ") must equal total adjustment quantity (" + qty + ")");
            }

            // Apply batch modifications
            for (var entry : executedBatchAllocations) {
                StockReceiptDetail batch = entry.getKey();
                BigDecimal allocQty = entry.getValue();
                BigDecimal currentRemaining = batch.getRemainingQuantity() != null ? batch.getRemainingQuantity() : BigDecimal.ZERO;

                if ("INCREASE".equals(typeStr)) {
                    batch.setRemainingQuantity(currentRemaining.add(allocQty));
                } else {
                    batch.setRemainingQuantity(currentRemaining.subtract(allocQty));
                }
                stockReceiptDetailRepository.save(batch);
            }
        } else {
            // No allocations provided
            if ("INCREASE".equals(typeStr) && groupedAllocations != null && !groupedAllocations.isEmpty()) {
                throw new BadRequestException("Stock increase requires selecting specific receipt details to restore quantity to, in order to preserve unit cost and receipt history.");
            }

            if (Boolean.TRUE.equals(product.getTrackExpiry())) {
                if (!confirmedBatches.isEmpty()) {
                    throw new BadRequestException("Batch allocation is required for expiry-tracked product: "
                            + product.getProductName() + ". Please select the affected batch(es).");
                } else if ("INCREASE".equals(typeStr)) {
                    throw new BadRequestException("Cannot increase stock without a valid batch for expiry-tracked product: "
                            + product.getProductName() + ". Newly discovered batches must be imported via Stock Receipt (UC36).");
                }
            } else {
                // Direct Stock (trackExpiry = false): Automatic deterministic FIFO / Reverse-FIFO allocation
                if ("DECREASE".equals(typeStr)) {
                    // Decision 1: Deterministic FIFO allocation across confirmed receipt details
                    List<StockReceiptDetail> fifoBatches = confirmedBatches.stream()
                            .filter(d -> d.getRemainingQuantity() != null && d.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                            .sorted(Comparator
                                    .comparing((StockReceiptDetail d) -> d.getStockReceipt() != null && d.getStockReceipt().getReceivedAt() != null
                                            ? d.getStockReceipt().getReceivedAt()
                                            : LocalDateTime.MAX)
                                    .thenComparing(StockReceiptDetail::getStockReceiptItemId))
                            .toList();

                    BigDecimal totalAvailableInBatches = fifoBatches.stream()
                            .map(StockReceiptDetail::getRemainingQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (qty.compareTo(totalAvailableInBatches) > 0) {
                        throw new BadRequestException("Không đủ tồn kho phiếu nhập cho " + product.getProductCode()
                                + ". Tồn kho phiếu nhập khả dụng: " + totalAvailableInBatches.stripTrailingZeros().toPlainString()
                                + ", yêu cầu giảm: " + qty.stripTrailingZeros().toPlainString() + ".");
                    }

                    BigDecimal remainingToDeduct = qty;
                    for (StockReceiptDetail detail : fifoBatches) {
                        if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;
                        BigDecimal detailRemaining = detail.getRemainingQuantity();
                        if (detailRemaining == null || detailRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                        BigDecimal deduct = detailRemaining.min(remainingToDeduct);
                        detail.setRemainingQuantity(detailRemaining.subtract(deduct));
                        remainingToDeduct = remainingToDeduct.subtract(deduct);

                        stockReceiptDetailRepository.save(detail);
                        executedBatchAllocations.add(java.util.Map.entry(detail, deduct));
                    }

                    if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
                        throw new BadRequestException("Không thể phân bổ hết số lượng giảm " + qty.stripTrailingZeros().toPlainString()
                                + " cho sản phẩm " + product.getProductCode());
                    }
                } else {
                    // Decision 2: Bounded Reverse-FIFO restoration into previously depleted receipt details (no unrestricted overflow)
                    List<StockReceiptDetail> candidateBatches = confirmedBatches.stream()
                            .filter(d -> {
                                BigDecimal orig = d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO;
                                BigDecimal rem = d.getRemainingQuantity() != null ? d.getRemainingQuantity() : BigDecimal.ZERO;
                                return orig.compareTo(rem) > 0;
                            })
                            .sorted(Comparator
                                    .comparing((StockReceiptDetail d) -> d.getStockReceipt() != null && d.getStockReceipt().getReceivedAt() != null
                                            ? d.getStockReceipt().getReceivedAt()
                                            : LocalDateTime.MIN, Comparator.reverseOrder())
                                    .thenComparing(StockReceiptDetail::getStockReceiptItemId, Comparator.reverseOrder()))
                            .toList();

                    BigDecimal totalRestorationCapacity = candidateBatches.stream()
                            .map(d -> {
                                BigDecimal orig = d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO;
                                BigDecimal rem = d.getRemainingQuantity() != null ? d.getRemainingQuantity() : BigDecimal.ZERO;
                                BigDecimal cap = orig.subtract(rem);
                                return cap.compareTo(BigDecimal.ZERO) > 0 ? cap : BigDecimal.ZERO;
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (qty.compareTo(totalRestorationCapacity) > 0) {
                        throw new BadRequestException("Không thể tăng tồn kho " + qty.stripTrailingZeros().toPlainString()
                                + " cho " + product.getProductCode()
                                + ". Dung lượng khôi phục tối đa từ các phiếu nhập đã xuất là "
                                + totalRestorationCapacity.stripTrailingZeros().toPlainString()
                                + ". Hàng tồn kho mới vượt quá lịch sử phiếu nhập phải được nhập qua Phiếu Nhập Kho (UC36).");
                    }

                    BigDecimal remainingToRestore = qty;
                    for (StockReceiptDetail detail : candidateBatches) {
                        if (remainingToRestore.compareTo(BigDecimal.ZERO) <= 0) break;
                        BigDecimal orig = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
                        BigDecimal rem = detail.getRemainingQuantity() != null ? detail.getRemainingQuantity() : BigDecimal.ZERO;
                        BigDecimal cap = orig.subtract(rem);
                        if (cap.compareTo(BigDecimal.ZERO) <= 0) continue;

                        BigDecimal restore = cap.min(remainingToRestore);
                        detail.setRemainingQuantity(rem.add(restore));
                        remainingToRestore = remainingToRestore.subtract(restore);

                        stockReceiptDetailRepository.save(detail);
                        executedBatchAllocations.add(java.util.Map.entry(detail, restore));
                    }

                    if (remainingToRestore.compareTo(BigDecimal.ZERO) > 0) {
                        throw new BadRequestException("Không thể phân bổ hết số lượng tăng " + qty.stripTrailingZeros().toPlainString()
                                + " cho sản phẩm " + product.getProductCode());
                    }
                }
            }
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
        notificationService.checkStockLevelTransition(product, previousStock, newStock);

        // 7. Record StockMovement
        User currentUser = resolveCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        Long firstMovementId = null;

        if (!executedBatchAllocations.isEmpty()) {
            for (var entry : executedBatchAllocations) {
                StockReceiptDetail batch = entry.getKey();
                BigDecimal allocQty = entry.getValue();
                BigDecimal batchQtyChange = "INCREASE".equals(typeStr) ? allocQty : allocQty.negate();
                String batchReason = reason;
                if (executedBatchAllocations.size() > 1 && batch.getBatchNumber() != null) {
                    batchReason = reason + " [Batch: " + batch.getBatchNumber() + "]";
                }

                StockMovement m = new StockMovement();
                m.setProduct(product);
                m.setStockReceiptDetail(batch);
                m.setMovementType(StockMovementType.ADJUSTMENT);
                m.setQuantityChange(batchQtyChange);
                m.setReferenceType(StockReferenceType.STOCK_CHECK);
                m.setReferenceId(request.stockCheckId() != null ? request.stockCheckId() : 0L);
                m.setReason(batchReason);
                m.setCreatedBy(currentUser);
                m.setCreatedAt(now);

                StockMovement saved = stockMovementRepository.save(m);
                if (firstMovementId == null) {
                    firstMovementId = saved.getMovementId();
                }
            }
        } else {
            StockMovement m = new StockMovement();
            m.setProduct(product);
            m.setStockReceiptDetail(null);
            m.setMovementType(StockMovementType.ADJUSTMENT);
            m.setQuantityChange(quantityChange);
            m.setReferenceType(StockReferenceType.STOCK_CHECK);
            m.setReferenceId(request.stockCheckId() != null ? request.stockCheckId() : 0L);
            m.setReason(reason);
            m.setCreatedBy(currentUser);
            m.setCreatedAt(now);

            StockMovement saved = stockMovementRepository.save(m);
            firstMovementId = saved.getMovementId();
        }

        return new StockAdjustmentResponse(
                firstMovementId,
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
            validatePurchaseOrderEligibility(purchaseOrder, supplier);
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

        // 5b. Validate batch identity consistency for expiry-tracked products (UC36)
        validateBatchIdentityConsistency(validatedItems, null);

        // 5c. If a PO is linked, validate product membership and remaining quantities
        List<PurchaseOrderDetail> lockedPoDetails = null;
        if (purchaseOrder != null) {
            if (targetStatus == StockReceiptStatus.CONFIRMED) {
                lockedPoDetails = purchaseOrderDetailRepository.findByPurchaseOrderWithLock(purchaseOrder);
            }
            validatePoProductsAndQuantities(purchaseOrder, supplier, validatedItems, lockedPoDetails);
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
        List<StockReceiptDetail> savedDetails = new ArrayList<>();
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
            savedDetails.add(detail);

            if (targetStatus == StockReceiptStatus.CONFIRMED) {
                // Update product stock
                Product p = item.product;
                BigDecimal currentStock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
                BigDecimal newStock = currentStock.add(item.quantity);
                p.setStockQuantity(newStock);
                productRepository.save(p);
                notificationService.checkStockLevelTransition(p, currentStock, newStock);

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

        // 8. Update PO received quantities and transition PO status atomically if CONFIRMED
        if (purchaseOrder != null && targetStatus == StockReceiptStatus.CONFIRMED && lockedPoDetails != null) {
            updatePurchaseOrderFulfillment(purchaseOrder, lockedPoDetails, savedDetails);
            notificationService.sendStockReceiptConfirmed(receipt, purchaseOrder);
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

        List<StockReceiptDetail> details = stockReceiptDetailRepository.findByStockReceipt(receipt);
        if (details.isEmpty()) {
            throw new BadRequestException("Stock receipt must contain at least one product item to confirm");
        }

        // Validate perishable items and batch consistency before confirming (UC36)
        for (StockReceiptDetail d : details) {
            if (Boolean.TRUE.equals(d.getProduct().getTrackExpiry())) {
                if (d.getBatchNumber() == null || d.getBatchNumber().trim().isEmpty()) {
                    throw new BadRequestException("Batch number is required for perishable product: " + d.getProduct().getProductName());
                }
                if (d.getExpirationDate() == null) {
                    throw new BadRequestException("Expiration date is required for perishable product: " + d.getProduct().getProductName());
                }
                if (d.getExpirationDate().isBefore(LocalDate.now())) {
                    throw new BadRequestException("Expiration date cannot be in the past for product: " + d.getProduct().getProductName());
                }
            }
        }

        List<ItemValidationContext> validationItems = details.stream()
                .map(d -> new ItemValidationContext(
                        d.getProduct(),
                        d.getQuantity(),
                        d.getUnitCost(),
                        d.getBatchNumber(),
                        d.getExpirationDate(),
                        d.getSubtotal()
                ))
                .toList();
        validateBatchIdentityConsistency(validationItems, receipt.getStockReceiptId());

        // If linked to a PO, re-validate eligibility and quantities with locking
        PurchaseOrder linkedPo = receipt.getPurchaseOrder();
        List<PurchaseOrderDetail> lockedPoDetails = null;
        if (linkedPo != null) {
            final Long poId = linkedPo.getOrderId();
            linkedPo = purchaseOrderRepository.findById(poId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + poId));
            validatePurchaseOrderEligibility(linkedPo, receipt.getSupplier());
            lockedPoDetails = purchaseOrderDetailRepository.findByPurchaseOrderWithLock(linkedPo);
            validatePoProductsAndQuantities(linkedPo, receipt.getSupplier(), validationItems, lockedPoDetails);
        }

        LocalDateTime now = LocalDateTime.now();
        receipt.setStatus(StockReceiptStatus.CONFIRMED);
        receipt.setReceivedAt(now);
        receipt = stockReceiptRepository.save(receipt);

        User operator = resolveCurrentUser();
        for (StockReceiptDetail detail : details) {
            detail.setRemainingQuantity(detail.getQuantity());
            stockReceiptDetailRepository.save(detail);

            // Update product inventory
            Product p = detail.getProduct();
            BigDecimal currentStock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal newStock = currentStock.add(detail.getQuantity());
            p.setStockQuantity(newStock);
            productRepository.save(p);
            notificationService.checkStockLevelTransition(p, currentStock, newStock);

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

        // Update PO received quantities and transition PO status atomically
        if (linkedPo != null && lockedPoDetails != null) {
            updatePurchaseOrderFulfillment(linkedPo, lockedPoDetails, details);
            notificationService.sendStockReceiptConfirmed(receipt, linkedPo);
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
            validatePurchaseOrderEligibility(purchaseOrder, supplier);
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

        // 3b. Validate batch identity consistency for expiry-tracked products (UC36)
        validateBatchIdentityConsistency(validatedItems, receipt.getStockReceiptId());

        // 3c. If a PO is linked, validate product membership and remaining quantities
        if (purchaseOrder != null) {
            validatePoProductsAndQuantities(purchaseOrder, supplier, validatedItems, null);
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

    private record BatchIdentityKey(Long productId, String normalizedBatchNumber) {}
    private record BatchIdentityInfo(Product product, String originalBatchNumber, LocalDate expirationDate) {}

    /**
     * Validates batch identity consistency for expiry-tracked products (UC36):
     * 1. Multiple entries within the same receipt for the same product and batch number must have identical expiration dates.
     * 2. Any combination of product and batch number matching an existing confirmed batch in the system must have identical expiration dates.
     * If dates match, another receipt detail is allowed to be created (preserved as separate records).
     * If dates differ, a BadRequestException is thrown.
     *
     * @param items List of validated items or receipt details to check
     * @param excludeReceiptId Receipt ID to exclude from confirmed check (e.g. current receipt being confirmed or edited)
     */
    private void validateBatchIdentityConsistency(List<ItemValidationContext> items, Long excludeReceiptId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<BatchIdentityKey, BatchIdentityInfo> uniqueBatchesInReceipt = new LinkedHashMap<>();

        // 1. Intra-receipt consistency check (Requirement 6)
        for (ItemValidationContext item : items) {
            Product product = item.product();
            if (product != null && Boolean.TRUE.equals(product.getTrackExpiry())) {
                String rawBatch = item.batchNumber();
                String batchNumber = rawBatch != null ? rawBatch.trim() : "";
                LocalDate expirationDate = item.expirationDate();

                if (!batchNumber.isEmpty() && expirationDate != null) {
                    BatchIdentityKey key = new BatchIdentityKey(product.getProductId(), batchNumber.toLowerCase());
                    BatchIdentityInfo existing = uniqueBatchesInReceipt.get(key);
                    if (existing != null) {
                        if (!existing.expirationDate().equals(expirationDate)) {
                            throw new BadRequestException("Conflicting expiration dates for product '" + product.getProductName()
                                    + "' with batch number '" + existing.originalBatchNumber() + "' within the same receipt: "
                                    + existing.expirationDate() + " vs " + expirationDate
                                    + ". A physical batch must have a consistent expiration date.");
                        }
                    } else {
                        uniqueBatchesInReceipt.put(key, new BatchIdentityInfo(product, batchNumber, expirationDate));
                    }
                }
            }
        }

        // 2. Consistency check against existing confirmed batches in DB (Requirements 2, 3, 4, 5)
        for (Map.Entry<BatchIdentityKey, BatchIdentityInfo> entry : uniqueBatchesInReceipt.entrySet()) {
            BatchIdentityInfo info = entry.getValue();
            List<StockReceiptDetail> confirmedBatches = stockReceiptDetailRepository
                    .findConfirmedBatchesByProductAndBatchNumber(info.product().getProductId(), info.originalBatchNumber());

            for (StockReceiptDetail confirmed : confirmedBatches) {
                if (excludeReceiptId != null && confirmed.getStockReceipt() != null
                        && excludeReceiptId.equals(confirmed.getStockReceipt().getStockReceiptId())) {
                    continue;
                }

                if (confirmed.getExpirationDate() != null && !confirmed.getExpirationDate().equals(info.expirationDate())) {
                    String receiptRef = confirmed.getStockReceipt() != null ? confirmed.getStockReceipt().getReceiptNumber() : "existing";
                    throw new BadRequestException("Batch '" + info.originalBatchNumber() + "' for product '" + info.product().getProductName()
                            + "' already exists in confirmed stock receipt '" + receiptRef
                            + "' with expiration date " + confirmed.getExpirationDate()
                            + ". Cannot receive the same batch with a different expiration date (" + info.expirationDate() + ").");
                }
            }
        }
    }

    /**
     * Validates that the purchase order belongs to the given supplier and is in an eligible status
     * for receiving goods (ACCEPTED or PARTIALLY_FULFILLED).
     */
    private void validatePurchaseOrderEligibility(PurchaseOrder purchaseOrder, Supplier supplier) {
        if (purchaseOrder == null || supplier == null) {
            return;
        }

        if (purchaseOrder.getSupplier() == null
                || !purchaseOrder.getSupplier().getSupplierId().equals(supplier.getSupplierId())) {
            throw new BadRequestException("Selected purchase order " + purchaseOrder.getPoNumber()
                    + " does not belong to supplier " + supplier.getSupplierName());
        }

        PurchaseOrderStatus status = purchaseOrder.getStatus();
        if (status != PurchaseOrderStatus.ACCEPTED && status != PurchaseOrderStatus.PARTIALLY_FULFILLED) {
            throw new BadRequestException("Purchase order " + purchaseOrder.getPoNumber()
                    + " is not eligible for stock receipt. Current status: " + status
                    + ". Only ACCEPTED or PARTIALLY_FULFILLED orders can receive goods.");
        }
    }

    /**
     * Validates that received products belong to the selected purchase order and that received
     * quantities do not exceed remaining ordered quantities.
     */
    private void validatePoProductsAndQuantities(
            PurchaseOrder purchaseOrder,
            Supplier supplier,
            List<ItemValidationContext> items,
            List<PurchaseOrderDetail> lockedPoDetails) {

        if (purchaseOrder == null || items == null || items.isEmpty()) {
            return;
        }

        validatePurchaseOrderEligibility(purchaseOrder, supplier);

        List<PurchaseOrderDetail> poDetails = lockedPoDetails != null
                ? lockedPoDetails
                : purchaseOrderDetailRepository.findByPurchaseOrder(purchaseOrder);

        if (poDetails.isEmpty()) {
            throw new BadRequestException("Purchase order " + purchaseOrder.getPoNumber() + " contains no items.");
        }

        Map<Long, PurchaseOrderDetail> poDetailByProduct = new HashMap<>();
        for (PurchaseOrderDetail pod : poDetails) {
            if (pod.getProduct() != null) {
                poDetailByProduct.put(pod.getProduct().getProductId(), pod);
            }
        }

        Map<Long, BigDecimal> receiptQtyByProduct = new HashMap<>();
        for (ItemValidationContext item : items) {
            Long prodId = item.product().getProductId();
            receiptQtyByProduct.merge(prodId, item.quantity(), BigDecimal::add);
        }

        for (Map.Entry<Long, BigDecimal> entry : receiptQtyByProduct.entrySet()) {
            Long prodId = entry.getKey();
            BigDecimal totalReceivedQty = entry.getValue();

            PurchaseOrderDetail poDetail = poDetailByProduct.get(prodId);
            if (poDetail == null) {
                String prodName = items.stream()
                        .filter(it -> it.product().getProductId().equals(prodId))
                        .map(it -> it.product().getProductName())
                        .findFirst()
                        .orElse("ID " + prodId);
                throw new BadRequestException("Product '" + prodName + "' is not included in purchase order " + purchaseOrder.getPoNumber());
            }

            BigDecimal orderedQty = poDetail.getOrderedQuantity() != null
                    ? poDetail.getOrderedQuantity()
                    : poDetail.getRequestedQuantity();
            if (orderedQty == null) {
                orderedQty = BigDecimal.ZERO;
            }

            BigDecimal previouslyReceived = poDetail.getReceivedQuantity() != null
                    ? poDetail.getReceivedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal remainingQty = orderedQty.subtract(previouslyReceived);
            if (remainingQty.compareTo(BigDecimal.ZERO) < 0) {
                remainingQty = BigDecimal.ZERO;
            }

            if (totalReceivedQty.compareTo(remainingQty) > 0) {
                throw new BadRequestException("Received quantity (" + totalReceivedQty + ") for product '"
                        + poDetail.getProduct().getProductName() + "' exceeds remaining ordered quantity ("
                        + remainingQty + ") in purchase order " + purchaseOrder.getPoNumber()
                        + " (Ordered: " + orderedQty + ", Previously received: " + previouslyReceived + ")");
            }
        }
    }

    /**
     * Atomically increases PurchaseOrderDetail.receivedQuantity by the confirmed receipt quantities
     * and recalculates the overall PurchaseOrder status (PARTIALLY_FULFILLED or FULFILLED).
     */
    private void updatePurchaseOrderFulfillment(
            PurchaseOrder purchaseOrder,
            List<PurchaseOrderDetail> lockedPoDetails,
            List<StockReceiptDetail> receiptDetails) {

        if (purchaseOrder == null || lockedPoDetails == null || receiptDetails == null) {
            return;
        }

        Map<Long, PurchaseOrderDetail> poDetailByProduct = new HashMap<>();
        for (PurchaseOrderDetail pod : lockedPoDetails) {
            if (pod.getProduct() != null) {
                poDetailByProduct.put(pod.getProduct().getProductId(), pod);
            }
        }

        Map<Long, BigDecimal> receiptQtyByProduct = new HashMap<>();
        for (StockReceiptDetail rd : receiptDetails) {
            if (rd.getProduct() != null && rd.getQuantity() != null) {
                receiptQtyByProduct.merge(rd.getProduct().getProductId(), rd.getQuantity(), BigDecimal::add);
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : receiptQtyByProduct.entrySet()) {
            PurchaseOrderDetail pod = poDetailByProduct.get(entry.getKey());
            if (pod != null) {
                BigDecimal currentReceived = pod.getReceivedQuantity() != null
                        ? pod.getReceivedQuantity()
                        : BigDecimal.ZERO;
                pod.setReceivedQuantity(currentReceived.add(entry.getValue()));
                purchaseOrderDetailRepository.save(pod);
            }
        }

        boolean allFulfilled = true;
        boolean anyReceived = false;

        for (PurchaseOrderDetail pod : lockedPoDetails) {
            BigDecimal orderedQty = pod.getOrderedQuantity() != null
                    ? pod.getOrderedQuantity()
                    : pod.getRequestedQuantity();
            if (orderedQty == null) {
                orderedQty = BigDecimal.ZERO;
            }

            BigDecimal receivedQty = pod.getReceivedQuantity() != null
                    ? pod.getReceivedQuantity()
                    : BigDecimal.ZERO;

            if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                anyReceived = true;
            }

            if (receivedQty.compareTo(orderedQty) < 0) {
                allFulfilled = false;
            }
        }

        PurchaseOrderStatus nextStatus;
        if (allFulfilled) {
            nextStatus = PurchaseOrderStatus.FULFILLED;
        } else if (anyReceived) {
            nextStatus = PurchaseOrderStatus.PARTIALLY_FULFILLED;
        } else {
            nextStatus = purchaseOrder.getStatus();
        }

        purchaseOrder.setStatus(nextStatus);
        purchaseOrder.setUpdatedAt(LocalDateTime.now());
        purchaseOrderRepository.save(purchaseOrder);
    }

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
    public StockCheckSessionPageResponse getStockCheckSessions(int page, int size, String keyword) {
        int pageIndex = Math.max(0, page);
        int pageSize = (size > 0 && size <= 100) ? size : 10;
        String pattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize);
        Page<String> pageResult = stockCheckRepository.findDistinctCheckNumbers(pattern, pageRequest);

        List<String> checkNumbers = pageResult.getContent();
        if (checkNumbers.isEmpty()) {
            return new StockCheckSessionPageResponse(
                    List.of(),
                    pageIndex,
                    pageSize,
                    pageResult.getTotalElements(),
                    pageResult.getTotalPages()
            );
        }

        List<StockCheck> checks = stockCheckRepository.findByCheckNumberIn(checkNumbers);
        java.util.Map<String, List<StockCheck>> itemsBySession = checks.stream()
                .collect(Collectors.groupingBy(StockCheck::getCheckNumber));

        List<StockCheckSessionResponse> content = checkNumbers.stream()
                .map(cn -> {
                    List<StockCheck> sessionChecks = itemsBySession.get(cn);
                    if (sessionChecks == null || sessionChecks.isEmpty()) {
                        return null;
                    }
                    StockCheck first = sessionChecks.get(0);
                    String username = first.getCheckedBy() != null ? first.getCheckedBy().getUsername() : "System";
                    LocalDateTime checkedAt = first.getCheckedAt() != null ? first.getCheckedAt() : LocalDateTime.now();
                    return buildSessionResponse(cn, username, checkedAt, sessionChecks);
                })
                .filter(Objects::nonNull)
                .toList();

        return new StockCheckSessionPageResponse(
                content,
                pageIndex,
                pageSize,
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
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
                        .thenComparing(a -> a.batchNumber() != null ? a.batchNumber() : "")
                        .thenComparing(a -> a.alertId() != null ? a.alertId() : "")
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