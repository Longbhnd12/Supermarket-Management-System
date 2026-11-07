package com.sms.backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for price audit trail history (UC12: Manage Prices).
 */
public record PriceHistoryResponse(
        Long logId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal delta,
        String changedBy,
        String reason,
        LocalDateTime timestamp
) {
}

