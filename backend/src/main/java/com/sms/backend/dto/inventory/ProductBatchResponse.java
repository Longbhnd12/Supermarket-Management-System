package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO representing an available confirmed inventory batch for UC33 Adjust Stock.
 */
public record ProductBatchResponse(
        Long stockReceiptItemId,
        Long productId,
        String batchNumber,
        LocalDate expirationDate,
        BigDecimal remainingQuantity,
        BigDecimal receivedQuantity,
        BigDecimal unitCost,
        String receiptNumber,
        Long stockReceiptId,
        LocalDateTime receivedAt,
        boolean expired
) {
}

