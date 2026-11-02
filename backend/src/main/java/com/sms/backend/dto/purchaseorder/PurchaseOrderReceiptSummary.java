package com.sms.backend.dto.purchaseorder;

import com.sms.backend.enums.StockReceiptStatus;

import java.time.LocalDateTime;

/**
 * Summary DTO of a Stock Receipt linked to a Purchase Order.
 */
public record PurchaseOrderReceiptSummary(
        Long stockReceiptId,
        String receiptNumber,
        StockReceiptStatus status,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        String receivedByUsername
) {
}

