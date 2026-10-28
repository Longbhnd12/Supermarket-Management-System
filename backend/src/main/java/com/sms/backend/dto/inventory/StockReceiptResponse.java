package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for StockReceipt with its detail lines.
 */
public record StockReceiptResponse(
        Long stockReceiptId,
        String receiptNumber,
        Long supplierId,
        String supplierName,
        Long purchaseOrderId,
        String poNumber,
        Long createdById,
        String createdByUsername,
        BigDecimal totalCost,
        StockReceiptStatus status,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        List<StockReceiptDetailResponse> details
) {
}
