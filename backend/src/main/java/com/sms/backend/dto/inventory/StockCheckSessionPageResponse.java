package com.sms.backend.dto.inventory;

import java.util.List;

/**
 * UC32 — Paginated stock check audit sessions response.
 */
public record StockCheckSessionPageResponse(
        List<StockCheckSessionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

