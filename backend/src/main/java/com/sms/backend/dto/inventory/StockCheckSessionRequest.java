package com.sms.backend.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Session request DTO for submitting a stock check count session (UC32).
 */
public record StockCheckSessionRequest(
        @NotBlank(message = "Session reference number is required")
        String checkNumber,

        @NotEmpty(message = "At least one product item must be counted")
        @Valid
        List<StockCheckItemRequest> items
) {
}

