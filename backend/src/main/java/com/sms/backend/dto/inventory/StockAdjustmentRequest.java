package com.sms.backend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for UC33 — Adjust Stock.
 * Supports product-level adjustments, batch receipt detail allocations, and grouped physical batch allocations.
 */
public record StockAdjustmentRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        @NotBlank(message = "Adjustment type is required")
        String adjustmentType, // "INCREASE" or "DECREASE"

        @NotNull(message = "Adjustment quantity is required")
        @DecimalMin(value = "1", message = "Adjustment quantity must be greater than 0")
        BigDecimal adjustmentQuantity,

        @NotBlank(message = "Adjustment reason is required")
        @Size(max = 255, message = "Adjustment reason must not exceed 255 characters")
        String reason,

        Long stockCheckId,

        List<BatchAllocationRequest> batchAllocations,

        List<GroupedBatchAllocationRequest> groupedBatchAllocations
) {
    /**
     * Backwards-compatible constructor for non-batch adjustments.
     */
    public StockAdjustmentRequest(Long productId, String adjustmentType, BigDecimal adjustmentQuantity, String reason, Long stockCheckId) {
        this(productId, adjustmentType, adjustmentQuantity, reason, stockCheckId, null, null);
    }

    /**
     * Backwards-compatible constructor for item-level batch adjustments.
     */
    public StockAdjustmentRequest(Long productId, String adjustmentType, BigDecimal adjustmentQuantity, String reason, Long stockCheckId, List<BatchAllocationRequest> batchAllocations) {
        this(productId, adjustmentType, adjustmentQuantity, reason, stockCheckId, batchAllocations, null);
    }

    /**
     * Item allocation DTO specifying how much quantity is adjusted against a specific receipt detail.
     */
    public record BatchAllocationRequest(
            Long stockReceiptItemId,
            String batchNumber,
            LocalDate expirationDate,
            @NotNull(message = "Allocated quantity is required")
            @DecimalMin(value = "1", message = "Allocated quantity must be greater than 0")
            BigDecimal quantity
    ) {
        public BatchAllocationRequest(Long stockReceiptItemId, BigDecimal quantity) {
            this(stockReceiptItemId, null, null, quantity);
        }
    }

    /**
     * Grouped batch allocation DTO specifying how much quantity is reduced from a physical batch.
     * The backend deterministically allocates the reduction across the underlying confirmed receipt details.
     */
    public record GroupedBatchAllocationRequest(
            @NotBlank(message = "Batch number is required")
            String batchNumber,
            LocalDate expirationDate,
            @NotNull(message = "Allocated quantity is required")
            @DecimalMin(value = "1", message = "Allocated quantity must be greater than 0")
            BigDecimal quantity
    ) {}
}
