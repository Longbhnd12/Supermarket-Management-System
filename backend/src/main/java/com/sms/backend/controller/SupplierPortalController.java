package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest;
import com.sms.backend.dto.supplierportal.SupplierOrderDetailResponse;
import com.sms.backend.dto.supplierportal.SupplierOrderPageResponse;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.service.SupplierPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * UC48 - View Purchase Orders (Supplier), UC49 - Respond to Purchase Order.
 * Requires the SUPPLIER_PORTAL permission (SecurityConfig); data is limited to the logged-in supplier.
 */
@RestController
@RequestMapping("/api/v1/supplier-portal/purchase-orders")
@RequiredArgsConstructor
public class SupplierPortalController {

    private final SupplierPortalService supplierPortalService;

    @GetMapping
    public ResponseEntity<ApiResponse<SupplierOrderPageResponse>> getMyOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved",
                supplierPortalService.getMyOrders(keyword, status, fromDate, toDate, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierOrderDetailResponse>> getMyOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved", supplierPortalService.getMyOrder(id)));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<SupplierOrderDetailResponse>> respond(
            @PathVariable Long id, @Valid @RequestBody RespondPurchaseOrderRequest request) {
        // MSG06: Updated successfully.
        return ResponseEntity.ok(ApiResponse.success("Updated successfully.", supplierPortalService.respond(id, request)));
    }
}
