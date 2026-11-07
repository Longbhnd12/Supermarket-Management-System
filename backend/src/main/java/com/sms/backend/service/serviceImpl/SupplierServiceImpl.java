package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.supplier.SupplierRequest;
import com.sms.backend.dto.supplier.SupplierResponse;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.SystemLog;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.SystemLogRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SystemLogRepository systemLogRepository;
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
        if (supplierRepository.existsBySupplierCode(request.supplierCode())) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + request.supplierCode());
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.supplierCode().trim());
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setContactPerson(request.contactPerson() != null ? request.contactPerson().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setTaxCode(request.taxCode() != null ? request.taxCode().trim() : null);
        supplier.setStatus(SupplierStatus.ACTIVE);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());

        Supplier saved = supplierRepository.save(supplier);
        logAction("CREATE_SUPPLIER", saved.getSupplierId(), "Đăng ký nhà cung cấp mới: " + saved.getSupplierName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = findById(id);
        if (!supplier.getSupplierCode().equalsIgnoreCase(request.supplierCode().trim())
                && supplierRepository.existsBySupplierCode(request.supplierCode().trim())) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + request.supplierCode());
        }
        supplier.setSupplierCode(request.supplierCode().trim());
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setContactPerson(request.contactPerson() != null ? request.contactPerson().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setTaxCode(request.taxCode() != null ? request.taxCode().trim() : null);
        supplier.setUpdatedAt(LocalDateTime.now());

        Supplier saved = supplierRepository.save(supplier);
        logAction("UPDATE_SUPPLIER", saved.getSupplierId(), "Cập nhật thông tin nhà cung cấp: " + saved.getSupplierName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(Long id, SupplierStatus status) {
        Supplier supplier = findById(id);
        SupplierStatus oldStatus = supplier.getStatus();
        supplier.setStatus(status);
        supplier.setUpdatedAt(LocalDateTime.now());
        Supplier saved = supplierRepository.save(supplier);

        String action = (status == SupplierStatus.DISABLED || status == SupplierStatus.INACTIVE)
                ? "DISABLE_SUPPLIER" : "ENABLE_SUPPLIER";
        logAction(action, saved.getSupplierId(),
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
            SystemLog log = new SystemLog();
            log.setUser(currentUserProvider.currentUser().orElse(null));
            log.setAction(action);
            log.setEntityType("Supplier");
            log.setEntityId(entityId);
            log.setDescription(description);
            log.setIpAddress(currentUserProvider.clientIp());
            log.setCreatedAt(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception ignored) {
            // Audit log error must not fail business transaction
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
