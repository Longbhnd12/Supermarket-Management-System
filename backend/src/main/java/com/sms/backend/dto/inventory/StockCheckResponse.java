package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for StockCheck.
 */
public record StockCheckResponse(
        Long stockCheckId,
        String checkNumber,
        Long productId,
        String productName,
        String productCode,
        Long checkedById,
        String checkedByUsername,
        BigDecimal systemQuantity,
        BigDecimal actualQuantity,
        BigDecimal difference,
        String reason,
        String status,
        LocalDateTime checkedAt
) {
}
