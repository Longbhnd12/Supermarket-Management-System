package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.supplier.SupplierRequest;
import com.sms.backend.dto.supplier.SupplierResponse;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getAllSuppliers(
            @RequestParam(required = false) SupplierStatus status) {
        List<SupplierResponse> result = status != null
                ? supplierService.getSuppliersByStatus(status)
                : supplierService.getAllSuppliers();
        return ResponseEntity.ok(ApiResponse.success("Suppliers retrieved", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Supplier retrieved", supplierService.getSupplierById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse response = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier created", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Supplier updated", supplierService.updateSupplier(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplierStatus(
            @PathVariable Long id,
            @RequestParam(required = false) SupplierStatus status,
            @RequestBody(required = false) Map<String, String> body) {
        SupplierStatus newStatus = status;
        if (newStatus == null && body != null && body.containsKey("status")) {
            try {
                newStatus = SupplierStatus.valueOf(body.get("status").trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Trạng thái không hợp lệ: " + body.get("status"));
            }
        }
        if (newStatus == null) {
            throw new BadRequestException("Trạng thái là bắt buộc (ACTIVE, INACTIVE, DISABLED)");
        }
        SupplierResponse response = supplierService.updateStatus(id, newStatus);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái nhà cung cấp thành công", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
