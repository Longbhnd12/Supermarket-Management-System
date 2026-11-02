package com.sms.backend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO for correcting physical count of an unreconciled Stock Check item.
 */
public record UpdateStockCheckCountRequest(
        @NotNull(message = "Physical count quantity is required")
        @DecimalMin(value = "0", message = "Physical count must be non-negative (>= 0)")
        BigDecimal actualQuantity,

        String reason
) {
}

