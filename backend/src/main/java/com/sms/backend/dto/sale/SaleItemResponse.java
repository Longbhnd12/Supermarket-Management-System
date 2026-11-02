package com.sms.backend.dto.sale;

import java.math.BigDecimal;

/**
 * Response DTO for a single SaleItem.
 */
public record SaleItemResponse(
        Long saleItemId,
        Long productId,
        String productName,
        String productCode,
        Long promotionId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal subtotal
) {
}
