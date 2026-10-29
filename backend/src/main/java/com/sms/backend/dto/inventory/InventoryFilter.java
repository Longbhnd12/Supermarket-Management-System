package com.sms.backend.dto.inventory;

public record InventoryFilter(
        String keyword,
        Long categoryId,
        String stockStatus,
        int page,
        int size
) {
    public String normalizedKeyword() {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }
    public int clampedSize() {
        if (size <= 0) return 20;
        return Math.min(size, 200);
    }
}
