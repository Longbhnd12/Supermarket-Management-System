package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReportResponse(
        Long categoryId, String stockStatus, long totalProducts, long inStockProducts, long lowStockProducts,
        long outOfStockProducts, long nearExpiryProducts, BigDecimal totalStockUnits,
        List<InventoryCategorySummaryResponse> categories, List<InventoryReportItemResponse> products
) {}
