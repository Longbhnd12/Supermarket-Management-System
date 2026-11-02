package com.sms.backend.dto.purchaseorder;

import com.sms.backend.enums.PurchaseOrderStatus;

import java.time.LocalDate;

/**
 * Filter parameters for searching and paginating purchase orders and requests (UC50).
 */
public record PurchaseOrderFilter(
        String keyword,
        Long supplierId,
        PurchaseOrderStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int page,
        int size
) {
}

