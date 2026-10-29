package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptFilter;
import com.sms.backend.dto.inventory.StockReceiptListItem;
import com.sms.backend.dto.inventory.StockReceiptPageResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockCheckRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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
    public StockReceiptResponse confirmStockReceipt(Long id) {
        throw new UnsupportedOperationException("Not yet implemented - pending SDD review");
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
        return stockReceiptRepository.findById(id)
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
                List.of()
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