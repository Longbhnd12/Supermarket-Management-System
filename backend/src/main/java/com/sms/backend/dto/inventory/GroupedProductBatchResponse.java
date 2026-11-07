package com.sms.backend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO representing a grouped physical batch for UC33 Adjust Stock.
 * Groups confirmed receipt details by (productId, normalizedBatchNumber, expirationDate).
 */
public record GroupedProductBatchResponse(
        Long productId,
        String batchNumber,
        LocalDate expirationDate,
        BigDecimal totalRemainingQuantity,
        BigDecimal totalReceivedQuantity,
        int receiptCount,
        boolean expired,
        boolean conflictingDates,
        List<ProductBatchResponse> details
) {
}

