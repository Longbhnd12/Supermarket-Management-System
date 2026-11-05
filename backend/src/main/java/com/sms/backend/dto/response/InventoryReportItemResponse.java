package com.sms.backend.dto.response;

import java.math.BigDecimal;

public record InventoryReportItemResponse(
        Long productId, String productCode, String productName, Long categoryId, String categoryName,
        BigDecimal stockQuantity, BigDecimal minimumStockLevel, String stockStatus, boolean nearExpiry
) {}
