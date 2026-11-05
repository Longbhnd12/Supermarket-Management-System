package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single StockReceiptDetail line.
 * UC37 Details — includes all fields shown in the detail screen.
 */
public record StockReceiptDetailResponse(
        Long receiptDetailId,
        Long productId,
        String productName,
        String productCode,
        BigDecimal receivedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitCost,
        String batchNumber,
        LocalDate expirationDate,
        BigDecimal subtotal
) {
}
