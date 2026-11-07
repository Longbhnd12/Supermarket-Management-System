package com.sms.backend.dto.category;

import com.sms.backend.enums.GeneralStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a Category.
 */
public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        GeneralStatus status
) {
    public CategoryRequest(String categoryName, String description) {
        this(categoryName, description, GeneralStatus.ACTIVE);
    }
}
