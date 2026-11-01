package com.sms.backend.dto.purchaseorder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for rejecting a Purchase Request in UC47.
 */
public record RejectPurchaseOrderRequest(

        @NotBlank(message = "Rejection reason is required")
        @Size(max = 255, message = "Rejection reason must not exceed 255 characters")
        String reason,

        @Size(max = 255, message = "Manager note must not exceed 255 characters")
        String managerNote
) {
}

