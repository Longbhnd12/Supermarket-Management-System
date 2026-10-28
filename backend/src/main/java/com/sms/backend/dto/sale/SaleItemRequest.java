package com.sms.backend.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A single item line when creating or adding to a sale.
 * TODO (SDD): Confirm whether promotionId is resolved automatically or user-specified.
 */
public record SaleItemRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        Long promotionId,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
        BigDecimal quantity
) {
}
