package com.sms.backend.dto.product;

import com.sms.backend.enums.GeneralStatus;

import java.math.BigDecimal;

/**
 * Response DTO for Product.
 * Fields map directly from Product entity.
 */
public record ProductResponse(
        Long productId,
        Long categoryId,
        String categoryName,
        String productCode,
        String barcode,
        String productName,
        String unit,
        BigDecimal sellingPrice,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        Boolean trackExpiry,
        GeneralStatus status
) {
}
