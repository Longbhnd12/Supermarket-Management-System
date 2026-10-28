package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single StockReceiptDetail line.
 */
public record StockReceiptDetailResponse(
        Long receiptDetailId,
        Long productId,
        String productName,
        String productCode,
        BigDecimal receivedQuantity,
        BigDecimal unitCost,
        BigDecimal subtotal,
        LocalDate expiryDate
) {
}
