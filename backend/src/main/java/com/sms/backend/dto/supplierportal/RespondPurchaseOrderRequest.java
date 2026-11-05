package com.sms.backend.dto.supplierportal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC49 - Supplier response (SRS Table 3-45).
 * reason: mandatory for REJECT and PROPOSE_CHANGES.
 * items / proposedDeliveryDate: only used for PROPOSE_CHANGES.
 */
public record RespondPurchaseOrderRequest(
        @NotNull(message = "The Response Option field is required.")
        Action action,

        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason,

        LocalDate proposedDeliveryDate,

        List<@Valid ProposedItem> items
) {
    public enum Action {
        ACCEPT,
        REJECT,
        PROPOSE_CHANGES
    }

    public record ProposedItem(
            @NotNull(message = "Order line is required") Long orderDetailId,
            BigDecimal proposedQuantity,
            BigDecimal proposedUnitPrice
    ) {
    }
}
