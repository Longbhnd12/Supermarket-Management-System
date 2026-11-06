package com.sms.backend.dto.supplierportal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Internal review of a supplier's UC49 proposal.
 * Accepting a proposal revises the PO and sends it back to the supplier in SENT state.
 */
public record ReviewSupplierProposalRequest(
        @NotNull Decision decision,
        @Size(max = 255, message = "Review comment cannot exceed 255 characters") String comment
) {
    public enum Decision {
        ACCEPT,
        REJECT
    }
}
