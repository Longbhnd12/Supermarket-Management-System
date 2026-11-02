package com.sms.backend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Item request DTO for an individual product in a stock check counting session (UC32).
 */
public record StockCheckItemRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Actual counted quantity is required")
        @DecimalMin(value = "0", message = "Actual quantity cannot be negative")
        BigDecimal actualQuantity,

        String reason
) {
}

