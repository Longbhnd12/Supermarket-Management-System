package com.sms.backend.dto.product;

import com.sms.backend.enums.GeneralStatus;
import java.math.BigDecimal;

/**
 * Detailed Response DTO for UC13: View Product Details.
 * Enriches basic product info with stock safety evaluation, active promotions, and supplier info.
 */
public record ProductDetailResponse(
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
        GeneralStatus status,
        ActivePromotionDTO activePromotion,
        SupplierSummaryDTO primarySupplier,
        String specification
) {
}

