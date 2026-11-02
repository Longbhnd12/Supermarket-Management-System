package com.sms.backend.dto.inventory;

import java.util.List;

/**
 * UC37 — Paginated stock receipt list response.
 * Uses the existing StockReceiptResponse (without detail lines).
 */
public record StockReceiptPageResponse(
        List<StockReceiptListItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
