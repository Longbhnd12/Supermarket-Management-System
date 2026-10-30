package com.sms.backend.dto.inventory;

import com.sms.backend.enums.StockReceiptStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for UC36 — Create Stock Receipt.
 */
public record CreateStockReceiptRequest(
        String receiptNumber,

        @NotNull(message = "Supplier is required")
        Long supplierId,

        Long purchaseOrderId,

        StockReceiptStatus status,

        @NotEmpty(message = "Receipt must contain at least one product item")
        @Valid
        List<CreateStockReceiptItemRequest> items
) {
}

