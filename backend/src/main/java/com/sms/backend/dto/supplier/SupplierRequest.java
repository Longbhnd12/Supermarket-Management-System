package com.sms.backend.dto.supplier;

import com.sms.backend.enums.SupplierStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a Supplier (UC20).
 */
public record SupplierRequest(

        @NotBlank(message = "Supplier code is required")
        @Size(max = 50, message = "Supplier code must not exceed 50 characters")
        String supplierCode,

        @NotBlank(message = "Supplier name is required")
        @Size(max = 200, message = "Supplier name must not exceed 200 characters")
        String supplierName,

        @Size(max = 100, message = "Contact person must not exceed 100 characters")
        String contactPerson,

        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,

        @Size(max = 20, message = "Tax code must not exceed 20 characters")
        String taxCode,

        SupplierStatus status
) {
    public SupplierRequest(String supplierCode, String supplierName, String contactPerson,
                           String phone, String email, String address, String taxCode) {
        this(supplierCode, supplierName, contactPerson, phone, email, address, taxCode, null);
    }
}
