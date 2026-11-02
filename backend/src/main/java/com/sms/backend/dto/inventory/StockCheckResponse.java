package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for StockCheck (UC32).
 */
public record StockCheckResponse(
        Long stockCheckId,
        String checkNumber,
        Long productId,
        String productName,
        String productCode,
        String barcode,
        String categoryName,
        String unit,
        Long checkedById,
        String checkedByUsername,
        BigDecimal systemQuantity,
        BigDecimal actualQuantity,
        BigDecimal difference,
        BigDecimal currentStock,
        Boolean isStale,
        BigDecimal unitPrice,
        BigDecimal financialImpact,
        String reason,
        String status,
        LocalDateTime checkedAt
) {
    /**
     * Backward-compatible 18-parameter constructor.
     */
    public StockCheckResponse(
            Long stockCheckId,
            String checkNumber,
            Long productId,
            String productName,
            String productCode,
            String barcode,
            String categoryName,
            String unit,
            Long checkedById,
            String checkedByUsername,
            BigDecimal systemQuantity,
            BigDecimal actualQuantity,
            BigDecimal difference,
            BigDecimal unitPrice,
            BigDecimal financialImpact,
            String reason,
            String status,
            LocalDateTime checkedAt
    ) {
        this(
                stockCheckId,
                checkNumber,
                productId,
                productName,
                productCode,
                barcode,
                categoryName,
                unit,
                checkedById,
                checkedByUsername,
                systemQuantity,
                actualQuantity,
                difference,
                null,
                false,
                unitPrice,
                financialImpact,
                reason,
                status,
                checkedAt
        );
    }

    /**
     * Backward-compatible 13-parameter constructor.
     */
    public StockCheckResponse(
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
        this(
                stockCheckId,
                checkNumber,
                productId,
                productName,
                productCode,
                null,
                null,
                null,
                checkedById,
                checkedByUsername,
                systemQuantity,
                actualQuantity,
                difference,
                null,
                false,
                null,
                null,
                reason,
                status,
                checkedAt
        );
    }
}
