package com.sms.backend.dto.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Single detail item for approval in UC47.
 */
public record ApprovedItemRequest(

        Long orderDetailId,

        Long productId,

        @NotNull(message = "Approved quantity is required")
        @DecimalMin(value = "0.001", message = "Approved quantity must be greater than 0")
        BigDecimal approvedQuantity
) {
}

