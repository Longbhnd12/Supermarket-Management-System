package com.sms.backend.dto.supplierportal;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * UC49 - Purchase Order Detail seen by the Supplier.
 * canRespond = false when the status is no longer SENT (AT4: read-only).
 */
public record SupplierOrderDetailResponse(
        Long orderId,
        String poNumber,
        PurchaseOrderStatus status,
        LocalDateTime issuedAt,
        LocalDate expectedDeliveryDate,
        String approvedBy,
        String note,
        BigDecimal totalAmount,
        LocalDate proposedDeliveryDate,
        BigDecimal proposedTotal,
        String responseReason,
        LocalDateTime lastUpdatedAt,
        boolean canRespond,
        List<SupplierOrderItemResponse> items
) {
}
