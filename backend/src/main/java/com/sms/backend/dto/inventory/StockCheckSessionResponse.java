package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Session response DTO for UC32 — Check Stock & Stock Difference.
 */
public record StockCheckSessionResponse(
        String checkNumber,
        String checkedByUsername,
        LocalDateTime checkedAt,
        int totalSkus,
        int matchedCount,
        int discrepancyCount,
        int staleCount,
        BigDecimal netQuantityVariance,
        BigDecimal netFinancialVariance,
        List<StockCheckResponse> items
) {
    /**
     * Backward-compatible constructor without staleCount.
     */
    public StockCheckSessionResponse(
            String checkNumber,
            String checkedByUsername,
            LocalDateTime checkedAt,
            int totalSkus,
            int matchedCount,
            int discrepancyCount,
            BigDecimal netQuantityVariance,
            BigDecimal netFinancialVariance,
            List<StockCheckResponse> items
    ) {
        this(
                checkNumber,
                checkedByUsername,
                checkedAt,
                totalSkus,
                matchedCount,
                discrepancyCount,
                0,
                netQuantityVariance,
                netFinancialVariance,
                items
        );
    }
}

