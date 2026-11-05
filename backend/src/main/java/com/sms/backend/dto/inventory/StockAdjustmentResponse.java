package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for UC33 — Adjust Stock.
 */
public record StockAdjustmentResponse(
        Long movementId,
        Long productId,
        String productCode,
        String productName,
        String unit,
        String adjustmentType,
        BigDecimal previousStock,
        BigDecimal adjustmentQuantity,
        BigDecimal newStock,
        String reason,
        Long stockCheckId,
        String adjustedByUsername,
        LocalDateTime adjustedAt
) {
}

