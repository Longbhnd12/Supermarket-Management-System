package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC31 — Inventory List item.
 * stockStatus: IN_STOCK | LOW_STOCK | OUT_OF_STOCK  (quantity-level only, independent of expiry)
 * batchExpiryAlerts: per-batch expiry conditions for products that track expiry
 */
public record InventoryResponse(
        Long productId,
        String productCode,
        String barcode,
        String productName,
        Long categoryId,
        String categoryName,
        String unit,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        String stockStatus,
        List<BatchExpiryAlertInfo> batchExpiryAlerts
) {
    /**
     * Per-batch expiry info row displayed in the Batch Expiry Alert column.
     * alertType: NEAR_EXPIRY | EXPIRED
     */
    public record BatchExpiryAlertInfo(
            Long stockReceiptItemId,
            String batchNumber,
            String receiptNumber,
            BigDecimal remainingQuantity,
            LocalDate expirationDate,
            long daysToExpiry,
            String alertType        // "NEAR_EXPIRY" | "EXPIRED"
    ) {}
}

