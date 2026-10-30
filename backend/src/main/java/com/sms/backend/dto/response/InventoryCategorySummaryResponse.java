package com.sms.backend.dto.response;

import java.math.BigDecimal;

public record InventoryCategorySummaryResponse(
        Long categoryId, String categoryName, long productCount, BigDecimal stockQuantity,
        long lowStockProducts, long outOfStockProducts
) {}
