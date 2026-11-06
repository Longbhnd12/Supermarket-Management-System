package com.sms.backend.dto.supplierportal;

import java.util.List;

/**
 * UC48 - paginated Purchase Order List of the logged-in Supplier + status summary.
 */
public record SupplierOrderPageResponse(
        List<SupplierOrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        SupplierOrderStatusCounts counts
) {
}
