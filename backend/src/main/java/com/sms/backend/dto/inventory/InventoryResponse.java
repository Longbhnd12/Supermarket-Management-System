package com.sms.backend.dto.inventory;

import java.math.BigDecimal;

public record InventoryResponse(
        Long productId,
        String productCode,
        String barcode,
        String productName,
        Long categoryId,
        String categoryName,
        String unit,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        String stockStatus
) {
}
