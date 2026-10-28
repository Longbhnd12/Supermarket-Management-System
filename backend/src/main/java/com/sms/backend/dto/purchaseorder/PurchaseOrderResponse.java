package com.sms.backend.dto.purchaseorder;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for PurchaseOrder with its detail lines.
 */
public record PurchaseOrderResponse(
        Long orderId,
        String poNumber,
        Long supplierId,
        String supplierName,
        Long requestedById,
        String requestedByUsername,
        Long approvedById,
        String approvedByUsername,
        BigDecimal totalAmount,
        LocalDate expectedDeliveryDate,
        LocalDate proposedDeliveryDate,
        PurchaseOrderStatus status,
        String note,
        String responseReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PurchaseOrderDetailResponse> details
) {
}
