package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.dto.inventory.StockAlertFilter;
import com.sms.backend.dto.inventory.StockAlertSummaryResponse;
import com.sms.backend.dto.inventory.StockCheckItemRequest;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockCheckSessionPageResponse;
import com.sms.backend.dto.inventory.StockCheckSessionRequest;
import com.sms.backend.dto.inventory.StockCheckSessionResponse;
import com.sms.backend.dto.inventory.UpdateStockCheckCountRequest;
import com.sms.backend.dto.inventory.StockHistoryFilter;
import com.sms.backend.dto.inventory.StockMovementPageResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for inventory operations.
 *
 * UC31 endpoints:
 *   GET /api/v1/inventory         - Inventory List (paginated + filtered)
 *   GET /api/v1/inventory/export  - CSV export of filtered inventory
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // =========================================================================
    // UC31 - Inventory List
    // =========================================================================

    /**
     * GET /api/v1/inventory
     *
     * Query params:
     *   keyword    - partial match on productName, productCode, barcode
     *   categoryId - filter by category ID
     *   stockStatus - IN_STOCK | LOW_STOCK | OUT_OF_STOCK | NEAR_EXPIRY
     *   page       - 0-based page index (default 0)
     *   size       - page size (default 20)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<InventoryPageResponse>> getInventoryList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        InventoryFilter filter = new InventoryFilter(keyword, categoryId, stockStatus, page, size);
        InventoryPageResponse result = inventoryService.getInventoryList(filter);
        return ResponseEntity.ok(ApiResponse.success("Inventory list retrieved", result));
    }

    /**
     * GET /api/v1/inventory/export
     * Downloads the full filtered inventory as a UTF-8 CSV (with BOM for Excel).
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportInventoryCsv(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String stockStatus) {

        InventoryFilter filter = new InventoryFilter(keyword, categoryId, stockStatus, 0, Integer.MAX_VALUE);
        byte[] csv = inventoryService.exportInventoryCsv(filter);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("inventory.csv").build());
        headers.setContentLength(csv.length);

        return ResponseEntity.ok().headers(headers).body(csv);
    }

    // =========================================================================
    // UC33 - Adjust Stock
    // =========================================================================

    /**
     * POST /api/v1/inventory/adjust
     * Applies manual stock adjustment or discrepancy adjustment from stock check.
     */
    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request) {
        StockAdjustmentResponse result = inventoryService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", result));
    }

    /**
     * GET /api/v1/inventory/products/{productId}/batches
     * Retrieves confirmed inventory batches for a product to support batch allocation (UC33).
     */
    @GetMapping("/products/{productId}/batches")
    public ResponseEntity<ApiResponse<List<com.sms.backend.dto.inventory.ProductBatchResponse>>> getProductBatches(
            @PathVariable Long productId) {
        List<com.sms.backend.dto.inventory.ProductBatchResponse> result = inventoryService.getProductBatches(productId);
        return ResponseEntity.ok(ApiResponse.success("Product batches retrieved", result));
    }

    /**
     * GET /api/v1/inventory/products/{productId}/batches/grouped
     * Retrieves grouped confirmed physical batches for a product (UC33).
     */
    @GetMapping("/products/{productId}/batches/grouped")
    public ResponseEntity<ApiResponse<List<com.sms.backend.dto.inventory.GroupedProductBatchResponse>>> getGroupedProductBatches(
            @PathVariable Long productId) {
        List<com.sms.backend.dto.inventory.GroupedProductBatchResponse> result = inventoryService.getGroupedProductBatches(productId);
        return ResponseEntity.ok(ApiResponse.success("Grouped product batches retrieved", result));
    }

    // =========================================================================
    // Stock Receipts
    // =========================================================================

    @GetMapping("/receipts")
    public ResponseEntity<ApiResponse<List<StockReceiptResponse>>> getAllReceipts(
            @RequestParam(required = false) StockReceiptStatus status) {
        List<StockReceiptResponse> result = status != null
                ? inventoryService.getStockReceiptsByStatus(status)
                : inventoryService.getAllStockReceipts();
        return ResponseEntity.ok(ApiResponse.success("Stock receipts retrieved", result));
    }

    @GetMapping("/receipts/{id}")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> getReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock receipt retrieved",
                inventoryService.getStockReceiptById(id)));
    }

    @PostMapping("/receipts/{id}/confirm")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> confirmReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock receipt confirmed",
                inventoryService.confirmStockReceipt(id)));
    }

    // =========================================================================
    // UC34 - Stock History
    // =========================================================================

    /**
     * GET /api/v1/inventory/history
     * Retrieves paginated stock movement history with search, product, type, and date range filters.
     */
    @GetMapping({"/history", "/stock-history"})
    public ResponseEntity<ApiResponse<StockMovementPageResponse>> getStockHistory(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        StockHistoryFilter filter = new StockHistoryFilter(
                productId, keyword, movementType, startDate, endDate, page, size
        );
        StockMovementPageResponse result = inventoryService.getStockHistory(filter);
        return ResponseEntity.ok(ApiResponse.success("Stock history retrieved", result));
    }

    // =========================================================================
    // UC35 - Stock Alerts
    // =========================================================================

    /**
     * GET /api/v1/inventory/alerts
     * GET /api/v1/inventory/stock-alerts
     *
     * Retrieves stock alert summary counts and paginated alert items (Low Stock, Out of Stock, Near Expiry, Expired).
     *
     * Query params:
     *   keyword    - search by product name, SKU, or barcode
     *   categoryId - filter by category ID
     *   alertType  - ALL | LOW_STOCK | OUT_OF_STOCK | NEAR_EXPIRY | EXPIRED
     *   page       - 0-based page index (default 0)
     *   size       - page size (default 20)
     */
    @GetMapping({"/alerts", "/stock-alerts"})
    public ResponseEntity<ApiResponse<StockAlertSummaryResponse>> getStockAlerts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String alertType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        StockAlertFilter filter = new StockAlertFilter(keyword, categoryId, alertType, page, size);
        StockAlertSummaryResponse result = inventoryService.getStockAlerts(filter);
        return ResponseEntity.ok(ApiResponse.success("Stock alerts retrieved", result));
    }

    // =========================================================================
    // Stock Movements
    // =========================================================================

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) StockReferenceType referenceType,
            @RequestParam(required = false) Long referenceId) {

        List<StockMovementResponse> result;
        if (productId != null) {
            result = inventoryService.getMovementsByProduct(productId);
        } else if (referenceType != null && referenceId != null) {
            result = inventoryService.getMovementsByReference(referenceType, referenceId);
        } else if (movementType != null) {
            result = inventoryService.getMovementsByType(movementType);
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved", result));
    }

    // =========================================================================
    // UC32 - Stock Checks & Stock Difference
    // =========================================================================

    @GetMapping("/checks/next-session")
    public ResponseEntity<ApiResponse<String>> getNextStockCheckSession() {
        return ResponseEntity.ok(ApiResponse.success("Next session number generated",
                inventoryService.getNextStockCheckSessionNumber()));
    }

    @PostMapping("/checks")
    public ResponseEntity<ApiResponse<StockCheckSessionResponse>> performStockCheck(
            @Valid @RequestBody StockCheckSessionRequest request) {
        StockCheckSessionResponse response = inventoryService.performStockCheck(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock check session recorded successfully", response));
    }

    @GetMapping("/checks/session/{checkNumber}")
    public ResponseEntity<ApiResponse<StockCheckSessionResponse>> getStockCheckSession(
            @PathVariable String checkNumber) {
        return ResponseEntity.ok(ApiResponse.success("Stock check session retrieved",
                inventoryService.getStockCheckSession(checkNumber)));
    }

    @GetMapping("/checks/sessions")
    public ResponseEntity<ApiResponse<StockCheckSessionPageResponse>> getStockCheckSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success("Stock check sessions retrieved",
                inventoryService.getStockCheckSessions(page, size, keyword)));
    }

    @GetMapping("/checks")
    public ResponseEntity<ApiResponse<List<StockCheckResponse>>> getAllChecks(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String checkNumber) {
        List<StockCheckResponse> result;
        if (checkNumber != null && !checkNumber.isBlank()) {
            result = inventoryService.getStockCheckSession(checkNumber).items();
        } else if (productId != null) {
            result = inventoryService.getStockChecksByProduct(productId);
        } else {
            result = inventoryService.getAllStockChecks();
        }
        return ResponseEntity.ok(ApiResponse.success("Stock checks retrieved", result));
    }

    @GetMapping("/checks/{id}")
    public ResponseEntity<ApiResponse<StockCheckResponse>> getCheckById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock check retrieved",
                inventoryService.getStockCheckById(id)));
    }

    @PutMapping("/checks/{id}")
    public ResponseEntity<ApiResponse<StockCheckResponse>> updateStockCheckCount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockCheckCountRequest request) {
        StockCheckResponse response = inventoryService.updateStockCheckItemCount(id, request);
        return ResponseEntity.ok(ApiResponse.success("Physical count updated successfully", response));
    }
}