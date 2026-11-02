package com.sms.backend.dto.purchaseorder;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for PurchaseOrder with its detail lines and linked receipts.
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
        List<PurchaseOrderDetailResponse> details,
        List<PurchaseOrderReceiptSummary> receipts
) {
    /**
     * Backward-compatible 17-argument constructor defaulting receipts to an empty list.
     */
    public PurchaseOrderResponse(
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
        this(
                orderId,
                poNumber,
                supplierId,
                supplierName,
                requestedById,
                requestedByUsername,
                approvedById,
                approvedByUsername,
                totalAmount,
                expectedDeliveryDate,
                proposedDeliveryDate,
                status,
                note,
                responseReason,
                createdAt,
                updatedAt,
                details,
                List.of()
        );
    }
}
