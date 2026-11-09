package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.supplier.SupplierRequest;
import com.sms.backend.dto.supplier.SupplierResponse;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.SupplierService;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SystemLogService systemLogService;
    private final CurrentUserProvider currentUserProvider;

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
        String code = request.supplierCode().trim();
        if (supplierRepository.existsBySupplierCode(code)) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + code);
        }

        String taxCode = request.taxCode() != null ? request.taxCode().trim() : null;
        if (taxCode != null && !taxCode.isEmpty() && supplierRepository.existsByTaxCode(taxCode)) {
            throw new BadRequestException("Mã số thuế (MST) đã được đăng ký bởi nhà cung cấp khác: " + taxCode);
        }

        Supplier supplier = new Supplier();
        supplier.setSupplierCode(code);
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setContactPerson(request.contactPerson() != null ? request.contactPerson().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setTaxCode(taxCode != null && !taxCode.isEmpty() ? taxCode : null);
        supplier.setStatus(request.status() != null ? request.status() : SupplierStatus.ACTIVE);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());

        Supplier saved = supplierRepository.saveAndFlush(supplier);
        logAction("SUPPLIER_SAVED", saved.getSupplierId(), "Đăng ký nhà cung cấp mới: " + saved.getSupplierName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = findById(id);
        String code = request.supplierCode().trim();
        if (!supplier.getSupplierCode().equalsIgnoreCase(code)
                && supplierRepository.existsBySupplierCode(code)) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + code);
        }

        String taxCode = request.taxCode() != null ? request.taxCode().trim() : null;
        if (taxCode != null && !taxCode.isEmpty()) {
            supplierRepository.findByTaxCode(taxCode).ifPresent(other -> {
                if (!other.getSupplierId().equals(supplier.getSupplierId())) {
                    throw new BadRequestException("Mã số thuế (MST) đã được đăng ký bởi nhà cung cấp khác: " + taxCode);
                }
            });
        }

        supplier.setSupplierCode(code);
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setContactPerson(request.contactPerson() != null ? request.contactPerson().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setTaxCode(taxCode != null && !taxCode.isEmpty() ? taxCode : null);
        if (request.status() != null) {
            supplier.setStatus(request.status());
        }
        if (supplier.getCreatedAt() == null) {
            supplier.setCreatedAt(LocalDateTime.now());
        }
        supplier.setUpdatedAt(LocalDateTime.now());

        Supplier saved = supplierRepository.saveAndFlush(supplier);
        logAction("SUPPLIER_SAVED", saved.getSupplierId(), "Cập nhật thông tin nhà cung cấp: " + saved.getSupplierName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(Long id, SupplierStatus status) {
        Supplier supplier = findById(id);
        SupplierStatus oldStatus = supplier.getStatus();
        supplier.setStatus(status);
        if (supplier.getCreatedAt() == null) {
            supplier.setCreatedAt(LocalDateTime.now());
        }
        supplier.setUpdatedAt(LocalDateTime.now());
        Supplier saved = supplierRepository.saveAndFlush(supplier);

        logAction("STATUS_CHANGED", saved.getSupplierId(),
                "Thay đổi trạng thái nhà cung cấp từ " + oldStatus + " sang " + status + " (" + saved.getSupplierName() + ")");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSupplier(Long id) {
        updateStatus(id, SupplierStatus.DISABLED);
    }

    // --- helpers ---

    private Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));
    }

    private void logAction(String action, Long entityId, String description) {
        try {
            systemLogService.createLog(
                    currentUserProvider.currentUser().orElse(null),
                    action,
                    "Supplier",
                    entityId,
                    description,
                    currentUserProvider.clientIp()
            );
        } catch (Exception e) {
            log.warn("Không thể lưu log hệ thống cho hành động nhà cung cấp {}: {}", action, e.getMessage());
        }
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
