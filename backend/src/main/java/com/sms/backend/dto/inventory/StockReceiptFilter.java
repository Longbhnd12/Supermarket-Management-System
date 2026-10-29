package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockReceiptStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * UC37 — Stock Receipt List filter parameters.
 */
public record StockReceiptFilter(
        String keyword,          // matches receiptNumber or supplierName
        String productSku,       // matches SKU in receipt details
        Long supplierId,
        StockReceiptStatus status,
        LocalDate dateFrom,
        LocalDate dateTo,
        int page,
        int size
) {
    public String normalizedKeyword() {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    public String normalizedProductSku() {
        return (productSku == null || productSku.isBlank()) ? null : productSku.trim();
    }

    public int clampedSize() {
        if (size <= 0) return 20;
        return Math.min(size, 200);
    }

    /** Inclusive start of day for date range filter. */
    public LocalDateTime effectiveDateFrom() {
        return dateFrom != null ? dateFrom.atStartOfDay() : null;
    }

    /** Inclusive end of day for date range filter (including the entire To Date). */
    public LocalDateTime effectiveDateTo() {
        return dateTo != null ? dateTo.atTime(LocalTime.MAX) : null;
    }
}
