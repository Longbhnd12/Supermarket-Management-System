package com.sms.backend.dto.supplierportal;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UC48 - one row of the Supplier's Purchase Order List.
 * hasProposal: the supplier already proposed changes that the store has not answered yet.
 */
public record SupplierOrderSummaryResponse(
        Long orderId,
        String poNumber,
        LocalDateTime issuedAt,
        LocalDate expectedDeliveryDate,
        int itemCount,
        BigDecimal totalAmount,
        PurchaseOrderStatus status,
        boolean hasProposal
) {
}
