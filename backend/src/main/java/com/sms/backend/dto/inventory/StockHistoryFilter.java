package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockMovementType;

import java.time.LocalDate;

/**
 * Filter DTO for querying stock movement history (UC34).
 */
public record StockHistoryFilter(
        Long productId,
        String keyword,
        StockMovementType movementType,
        LocalDate startDate,
        LocalDate endDate,
        int page,
        int size
) {
    public StockHistoryFilter {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
    }
}

