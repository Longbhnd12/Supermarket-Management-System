package com.sms.backend.dto.purchaseorder;

import java.util.List;

/**
 * Paginated response for Purchase Orders & Requests list (UC50),
 * including status counts across all tabs for the current filter scope.
 */
public record PurchaseOrderPageResponse(
        List<PurchaseOrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        PurchaseOrderStatusCountsResponse statusCounts
) {
}

