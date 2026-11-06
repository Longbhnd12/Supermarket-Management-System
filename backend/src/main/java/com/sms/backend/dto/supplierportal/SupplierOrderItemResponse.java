package com.sms.backend.dto.supplierportal;

import java.math.BigDecimal;

/**
 * UC49 - one product line of a Purchase Order (SRS Table 3-45, Order Items Table).
 */
public record SupplierOrderItemResponse(
        Long orderDetailId,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal orderedQuantity,
        BigDecimal unitPrice,
        BigDecimal proposedQuantity,
        BigDecimal proposedUnitPrice,
        BigDecimal subtotal
) {
}
