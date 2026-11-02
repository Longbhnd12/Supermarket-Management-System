package com.sms.backend.dto.purchaseorder;

/**
 * Aggregated status counts for UC50 Purchase Orders & Requests list tabs.
 */
public record PurchaseOrderStatusCountsResponse(
        long all,
        long pendingApproval,
        long rejectedByManager,
        long sent,
        long accepted,
        long rejectedBySupplier,
        long partiallyFulfilled,
        long fulfilled
) {
}

