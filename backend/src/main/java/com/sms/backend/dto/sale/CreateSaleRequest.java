package com.sms.backend.dto.sale;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Temporary explicit operator identifier until authentication is implemented. */
public record CreateSaleRequest(
        @NotNull(message = "Cashier ID is required")
        @Positive(message = "Cashier ID must be positive") Long cashierId
) {}
