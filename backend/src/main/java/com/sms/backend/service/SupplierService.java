package com.sms.backend.service;

import com.sms.backend.dto.supplier.SupplierRequest;
import com.sms.backend.dto.supplier.SupplierResponse;
import com.sms.backend.enums.SupplierStatus;

import java.util.List;

/**
 * Service interface for Supplier operations.
 * TODO (SDD): Confirm whether a supplier with active POs can be deactivated.
 */
public interface SupplierService {

    List<SupplierResponse> getAllSuppliers();

    List<SupplierResponse> getSuppliersByStatus(SupplierStatus status);

    SupplierResponse getSupplierById(Long id);

    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse updateSupplier(Long id, SupplierRequest request);

    void deleteSupplier(Long id);
}
