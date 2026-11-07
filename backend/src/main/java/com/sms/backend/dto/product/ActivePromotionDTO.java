package com.sms.backend.dto.product;

import com.sms.backend.enums.PromotionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary of an active promotion applied to a product (UC13: View Product Details).
 */
public record ActivePromotionDTO(
        Long promotionId,
        String promotionName,
        PromotionType promotionType,
        BigDecimal discountValue,
        BigDecimal discountedPrice,
        Integer minQuantity,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}

