package com.sms.backend.dto.purchaseorder;

import java.math.BigDecimal;

/**
 * Response DTO for a single PurchaseOrderDetail line.
 */
public record PurchaseOrderDetailResponse(
        Long orderDetailId,
        Long productId,
        String productName,
        String productCode,
        BigDecimal orderedQuantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
