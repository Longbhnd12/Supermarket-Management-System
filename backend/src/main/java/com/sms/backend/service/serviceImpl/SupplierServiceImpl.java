package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.supplier.SupplierRequest;
import com.sms.backend.dto.supplier.SupplierResponse;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<SupplierResponse> getSuppliersByStatus(SupplierStatus status) {
        return supplierRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SupplierResponse getSupplierById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        // TODO: Implement – validate code uniqueness, create Supplier
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        // TODO: Implement – update Supplier fields
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deleteSupplier(Long id) {
        // TODO (SDD): Confirm deletion policy (soft vs hard, guard against active POs)
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getSupplierId(),
                s.getSupplierCode(),
                s.getSupplierName(),
                s.getContactPerson(),
                s.getPhone(),
                s.getEmail(),
                s.getAddress(),
                s.getTaxCode(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
