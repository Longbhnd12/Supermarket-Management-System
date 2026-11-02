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
        String unit,
        BigDecimal requestedQuantity,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    /**
     * Backward-compatible 9-argument constructor defaulting receivedQuantity to ZERO.
     */
    public PurchaseOrderDetailResponse(
            Long orderDetailId,
            Long productId,
            String productName,
            String productCode,
            String unit,
            BigDecimal requestedQuantity,
            BigDecimal orderedQuantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        this(
                orderDetailId,
                productId,
                productName,
                productCode,
                unit,
                requestedQuantity,
                orderedQuantity,
                BigDecimal.ZERO,
                unitPrice,
                subtotal
        );
    }
}
