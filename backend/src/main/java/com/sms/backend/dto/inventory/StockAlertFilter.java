package com.sms.backend.dto.inventory;

/**
 * Filter DTO for querying stock alerts (UC35).
 */
public record StockAlertFilter(
        String keyword,
        Long categoryId,
        String alertType,
        int page,
        int size
) {
    public StockAlertFilter {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
    }

    public String normalizedKeyword() {
        return keyword != null && !keyword.isBlank() ? keyword.trim() : null;
    }
}

