package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReferenceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for StockMovement.
 * Maps from StockMovement entity.
 */
public record StockMovementResponse(
        Long movementId,
        Long productId,
        String productName,
        String productCode,
        StockMovementType movementType,
        BigDecimal quantityChange,
        StockReferenceType referenceType,
        Long referenceId,
        String reason,
        Long createdById,
        String createdByUsername,
        LocalDateTime createdAt
) {
}
