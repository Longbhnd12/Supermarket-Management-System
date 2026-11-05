package com.sms.backend.dto.inventory;

import java.util.List;

/**
 * Summary response DTO for stock alerts (UC35).
 * Contains aggregate counts for all four alert categories along with paginated alert items.
 */
public record StockAlertSummaryResponse(
        long lowStockCount,
        long outOfStockCount,
        long nearExpiryCount,
        long expiredCount,
        long totalAlerts,
        List<StockAlertItemResponse> alerts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

