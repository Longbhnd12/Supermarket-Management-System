package com.sms.backend.dto.sale;

import com.sms.backend.enums.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a Sale (with its items).
 */
public record SaleResponse(
        Long saleId,
        String saleNumber,
        String invoiceNumber,
        Long cashierId,
        String cashierUsername,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        SaleStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<SaleItemResponse> items
) {
}
