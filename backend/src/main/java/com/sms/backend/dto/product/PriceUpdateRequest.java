package com.sms.backend.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO for updating a product's selling price (UC12: Manage Prices).
 */
public record PriceUpdateRequest(

        @NotNull(message = "Giá bán mới không được để trống")
        @DecimalMin(value = "0.0", inclusive = true, message = "Giá bán phải lớn hơn hoặc bằng 0")
        BigDecimal newSellingPrice,

        String reason
) {
}

