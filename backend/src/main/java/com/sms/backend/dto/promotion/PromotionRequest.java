package com.sms.backend.dto.promotion;

import com.sms.backend.enums.PromotionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating or updating a Promotion.
 */
public record PromotionRequest(

        @NotBlank(message = "Promotion name is required")
        @Size(max = 150, message = "Promotion name must not exceed 150 characters")
        String promotionName,

        @NotNull(message = "Promotion type is required")
        PromotionType promotionType,

        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be greater than 0")
        BigDecimal discountValue,

        Long categoryId,

        Long productId,

        List<Long> productIds,

        @NotNull(message = "Start time is required")
        LocalDateTime startAt,

        @NotNull(message = "End time is required")
        LocalDateTime endAt
) {
}
