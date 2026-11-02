package com.sms.backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a Category.
 * TODO (SDD): Confirm field-level validation constraints and whether
 *             description is optional.
 */
public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description
) {
}
