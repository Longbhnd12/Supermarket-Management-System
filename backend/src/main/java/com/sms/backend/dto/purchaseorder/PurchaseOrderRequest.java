package com.sms.backend.dto.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a PurchaseOrder / Purchase Request.
 */
public record PurchaseOrderRequest(

        String poNumber,

        @NotNull(message = "Supplier ID is required")
        Long supplierId,

        @NotNull(message = "Expected delivery date is required")
        LocalDate expectedDeliveryDate,

        @Size(max = 255, message = "Note must not exceed 255 characters")
        String note,

        @NotEmpty(message = "At least one product item is required")
        @Valid
        List<PurchaseOrderDetailRequest> details
) {
}
