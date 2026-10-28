package com.sms.backend.dto.supplier;

import com.sms.backend.enums.SupplierStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for Supplier.
 */
public record SupplierResponse(
        Long supplierId,
        String supplierCode,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxCode,
        SupplierStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
