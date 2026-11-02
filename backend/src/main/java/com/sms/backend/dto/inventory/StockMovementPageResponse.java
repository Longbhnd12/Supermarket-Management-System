package com.sms.backend.dto.inventory;

import java.util.List;

/**
 * UC34 — Paginated stock movement history response.
 */
public record StockMovementPageResponse(
        List<StockMovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

