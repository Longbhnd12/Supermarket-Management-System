package com.sms.backend.dto.supplierportal;

/**
 * UC48 - Status Summary cards.
 */
public record SupplierOrderStatusCounts(
        long awaitingResponse,
        long accepted,
        long rejectedBySupplier,
        long partiallyFulfilled,
        long fulfilled
) {
}
