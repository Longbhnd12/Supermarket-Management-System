package com.sms.backend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for UC33 — Adjust Stock.
 */
public record StockAdjustmentRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        @NotBlank(message = "Adjustment type is required")
        String adjustmentType, // "INCREASE" or "DECREASE"

        @NotNull(message = "Adjustment quantity is required")
        @DecimalMin(value = "1", message = "Adjustment quantity must be greater than 0")
        BigDecimal adjustmentQuantity,

        @NotBlank(message = "Adjustment reason is required")
        @Size(max = 255, message = "Adjustment reason must not exceed 255 characters")
        String reason,

        Long stockCheckId
) {
}

