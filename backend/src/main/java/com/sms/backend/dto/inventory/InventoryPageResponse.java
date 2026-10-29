package com.sms.backend.dto.inventory;

import java.util.List;

public record InventoryPageResponse(
        List<InventoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
