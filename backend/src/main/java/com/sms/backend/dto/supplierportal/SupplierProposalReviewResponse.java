package com.sms.backend.dto.supplierportal;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SupplierProposalReviewResponse(
        Long orderId,
        String poNumber,
        PurchaseOrderStatus status,
        ReviewSupplierProposalRequest.Decision decision,
        LocalDate expectedDeliveryDate,
        BigDecimal totalAmount,
        String comment,
        LocalDateTime reviewedAt
) {
}
