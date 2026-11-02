package com.sms.backend.dto.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for approving a Purchase Request in UC47.
 */
public record ApprovePurchaseOrderRequest(

        @Size(max = 255, message = "Manager note must not exceed 255 characters")
        String managerNote,

        @Valid
        List<ApprovedItemRequest> items
) {
}

