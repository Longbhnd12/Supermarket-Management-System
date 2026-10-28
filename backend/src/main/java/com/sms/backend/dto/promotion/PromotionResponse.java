package com.sms.backend.dto.promotion;

import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Promotion.
 */
public record PromotionResponse(
        Long promotionId,
        String promotionName,
        PromotionType promotionType,
        BigDecimal discountValue,
        Long categoryId,
        String categoryName,
        Long productId,
        String productName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        GeneralStatus status,
        Long createdById,
        String createdByUsername
) {
}
