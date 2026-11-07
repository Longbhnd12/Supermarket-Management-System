package com.sms.backend.dto.product;

/**
 * Summary of a primary supplier associated with a product (UC13: View Product Details).
 */
public record SupplierSummaryDTO(
        Long supplierId,
        String supplierCode,
        String supplierName,
        String contactPerson,
        String phone,
        String sla
) {
}

