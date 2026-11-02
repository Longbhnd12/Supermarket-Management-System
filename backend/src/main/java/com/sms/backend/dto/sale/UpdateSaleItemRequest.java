package com.sms.backend.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Replaces the quantity, rather than incrementing it. */
public record UpdateSaleItemRequest(
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
        @Digits(integer = 9, fraction = 3, message = "Quantity supports 9 integer and 3 decimal digits")
        BigDecimal quantity
) {}
