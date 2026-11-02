package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.StockReceiptFilter;
import com.sms.backend.dto.inventory.StockReceiptPageResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for UC37 — Stock Receipt List.
 *
 * Endpoints:
 *   GET /api/v1/stock-receipts         - paginated + filtered list (UC37)
 *   GET /api/v1/stock-receipts/{id}    - single receipt with details (placeholder for UC37 Details)
 */
@RestController
@RequestMapping("/api/v1/stock-receipts")
@RequiredArgsConstructor
public class StockReceiptController {

    private final InventoryService inventoryService;

    // =========================================================================
    // UC37 — Stock Receipt List
    // =========================================================================

    /**
     * GET /api/v1/stock-receipts
     *
     * Query parameters:
     *   keyword     - partial match on receiptNumber or supplierName
     *   productSku  - partial match on a detail item's product SKU
     *   supplierId  - exact supplier ID
     *   status      - DRAFT | CONFIRMED | CANCELLED
     *   dateFrom    - inclusive start date (yyyy-MM-dd), matched against receivedAt or createdAt
     *   dateTo      - inclusive end date (yyyy-MM-dd)
     *   page        - 0-based page index (default 0)
     *   size        - page size: 5, 10, 20, 50 (default 20)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<StockReceiptPageResponse>> getStockReceiptList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productSku,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) StockReceiptStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Validate date range
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "From Date must not be later than To Date", null));
        }
        // Clamp pagination
        if (page < 0) page = 0;
        if (size <= 0) size = 20;

        StockReceiptFilter filter = new StockReceiptFilter(
                keyword, productSku, supplierId, status, dateFrom, dateTo, page, size
        );

        StockReceiptPageResponse result = inventoryService.getStockReceiptList(filter);
        return ResponseEntity.ok(ApiResponse.success("Stock receipt list retrieved", result));
    }

    /**
     * GET /api/v1/stock-receipts/{id}
     * Returns a single stock receipt with its detail lines.
     * Reuses the existing StockReceiptResponse (includes details via toReceiptResponse).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> getStockReceiptById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock receipt retrieved",
                inventoryService.getStockReceiptById(id)));
    }

    // =========================================================================
    // UC36 — Create Stock Receipt (Import Stock)
    // =========================================================================

    /**
     * GET /api/v1/stock-receipts/next-number
     * Returns the next auto-generated unique stock receipt identifier (e.g. SR0004).
     */
    @GetMapping("/next-number")
    public ResponseEntity<ApiResponse<String>> getNextReceiptNumber() {
        return ResponseEntity.ok(ApiResponse.success("Next receipt number generated",
                inventoryService.getNextReceiptNumber()));
    }

    /**
     * POST /api/v1/stock-receipts
     * Creates a new stock receipt with supplier and imported product batch lines.
     * If status is CONFIRMED (default), updates inventory and logs StockMovement records.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockReceiptResponse>> createStockReceipt(
            @Valid @RequestBody CreateStockReceiptRequest request) {
        StockReceiptResponse response = inventoryService.createStockReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock receipt created successfully", response));
    }

    /**
     * POST /api/v1/stock-receipts/{id}/confirm
     * Confirms an existing DRAFT stock receipt, updates inventory stock,
     * sets remainingQuantity on details, and logs StockMovement records.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> confirmStockReceipt(
            @PathVariable Long id) {
        StockReceiptResponse response = inventoryService.confirmStockReceipt(id);
        return ResponseEntity.ok(ApiResponse.success("Stock receipt confirmed successfully", response));
    }
}
