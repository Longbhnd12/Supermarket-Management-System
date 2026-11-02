package com.sms.backend.dto.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A single detail line for a PurchaseOrderRequest.
 */
public record PurchaseOrderDetailRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @DecimalMin(value = "0.001", message = "Requested quantity must be greater than 0")
        BigDecimal requestedQuantity,

        BigDecimal orderedQuantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", message = "Unit price must not be negative")
        BigDecimal unitPrice
) {
    public BigDecimal effectiveQuantity() {
        if (requestedQuantity != null) {
            return requestedQuantity;
        }
        return orderedQuantity;
    }
}
