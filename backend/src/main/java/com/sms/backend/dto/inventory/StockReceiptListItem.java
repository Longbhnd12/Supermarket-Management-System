package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UC37 — Lean response for a single stock receipt row in the list.
 * Does NOT include detail line items (loaded separately for UC37 Details).
 */
public record StockReceiptListItem(
        Long stockReceiptId,
        String receiptNumber,
        Long supplierId,
        String supplierName,
        BigDecimal totalCost,
        StockReceiptStatus status,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        String createdByUsername
) {
}
