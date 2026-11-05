package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item response DTO for individual stock alert records (UC35).
 * Represents an alert condition: LOW_STOCK, OUT_OF_STOCK, NEAR_EXPIRY, or EXPIRED.
 */
public record StockAlertItemResponse(
        String alertId,
        Long productId,
        String productCode,
        String barcode,
        String productName,
        Long categoryId,
        String categoryName,
        String unit,
        String alertType,
        BigDecimal currentStock,
        BigDecimal safetyMin,
        String batchNumber,
        BigDecimal batchRemainingQuantity,
        LocalDate expiryDate,
        Long daysToExpiry,
        String receiptNumber
) {
}

