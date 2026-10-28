package com.sms.backend.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request DTO for creating or updating a Product.
 * TODO (SDD): Confirm whether productCode is auto-generated or user-supplied.
 * TODO (SDD): Confirm barcode uniqueness rules and whether it can be omitted.
 */
public record ProductRequest(

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotBlank(message = "Product code is required")
        @Size(max = 50, message = "Product code must not exceed 50 characters")
        String productCode,

        @Size(max = 100, message = "Barcode must not exceed 100 characters")
        String barcode,

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name must not exceed 200 characters")
        String productName,

        @NotBlank(message = "Unit is required")
        @Size(max = 30, message = "Unit must not exceed 30 characters")
        String unit,

        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Selling price must be greater than 0")
        BigDecimal sellingPrice,

        @NotNull(message = "Minimum stock level is required")
        @DecimalMin(value = "0.0", message = "Minimum stock level must not be negative")
        BigDecimal minimumStockLevel,

        Boolean trackExpiry
) {
}
