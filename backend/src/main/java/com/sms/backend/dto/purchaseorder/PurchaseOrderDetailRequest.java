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

        @NotNull(message = "Ordered quantity is required")
        @DecimalMin(value = "0.001", message = "Ordered quantity must be greater than 0")
        BigDecimal orderedQuantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Unit price must be greater than 0")
        BigDecimal unitPrice
) {
}
