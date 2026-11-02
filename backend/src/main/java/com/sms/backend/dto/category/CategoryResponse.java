package com.sms.backend.dto.category;

import com.sms.backend.enums.GeneralStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for Category.
 * Fields map directly from Category entity.
 */
public record CategoryResponse(
        Long categoryId,
        String categoryName,
        String description,
        GeneralStatus status,
        LocalDateTime createdAt
) {
}
