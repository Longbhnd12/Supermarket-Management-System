package com.sms.backend.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A single item line when creating or adding to a sale.
 * Promotions are resolved automatically by UC27; promotionId must be omitted/null.
 */
public record SaleItemRequest(

        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        Long promotionId,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
        @Digits(integer = 9, fraction = 3, message = "Quantity supports 9 integer and 3 decimal digits")
        BigDecimal quantity
) {
}
