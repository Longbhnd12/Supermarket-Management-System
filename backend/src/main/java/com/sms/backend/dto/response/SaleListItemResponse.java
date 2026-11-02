package com.sms.backend.dto.response;

import com.sms.backend.enums.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Compact sale data for UC21. Invoice line items belong to the sale-detail use case. */
public record SaleListItemResponse(
        Long saleId,
        String saleNumber,
        String invoiceNumber,
        Long cashierId,
        String cashierUsername,
        BigDecimal totalAmount,
        SaleStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
