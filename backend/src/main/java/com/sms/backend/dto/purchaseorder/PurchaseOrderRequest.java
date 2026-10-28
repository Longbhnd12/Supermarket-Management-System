package com.sms.backend.dto.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a PurchaseOrder.
 * TODO (SDD): Confirm whether poNumber is auto-generated or user-supplied.
 * TODO (SDD): Confirm approval workflow – does creation auto-assign status?
 */
public record PurchaseOrderRequest(

        @NotNull(message = "Supplier ID is required")
        Long supplierId,

        @NotNull(message = "Expected delivery date is required")
        LocalDate expectedDeliveryDate,

        @Size(max = 255, message = "Note must not exceed 255 characters")
        String note,

        @NotNull(message = "Order details are required")
        @Valid
        List<PurchaseOrderDetailRequest> details
) {
}
