package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.ApprovePurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderFilter;
import com.sms.backend.dto.purchaseorder.PurchaseOrderPageResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderStatusCountsResponse;
import com.sms.backend.dto.purchaseorder.RejectPurchaseOrderRequest;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for Purchase Order & Purchase Request operations.
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PurchaseOrderPageResponse>> getPurchaseOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PurchaseOrderFilter filter = new PurchaseOrderFilter(keyword, supplierId, status, startDate, endDate, page, size);
        PurchaseOrderPageResponse response = purchaseOrderService.getPurchaseOrdersPage(filter);
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved", response));
    }

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<PurchaseOrderStatusCountsResponse>> getStatusCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PurchaseOrderFilter filter = new PurchaseOrderFilter(keyword, supplierId, null, startDate, endDate, 0, 0);
        return ResponseEntity.ok(ApiResponse.success("Status counts retrieved",
                purchaseOrderService.getStatusCounts(filter)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPendingPurchaseRequests() {
        return ResponseEntity.ok(ApiResponse.success("Pending purchase requests retrieved",
                purchaseOrderService.getPendingPurchaseRequests()));
    }

    @GetMapping("/next-number")
    public ResponseEntity<ApiResponse<String>> getNextRequestNumber() {
        return ResponseEntity.ok(ApiResponse.success("Next purchase request number generated",
                purchaseOrderService.getNextRequestNumber()));
    }

    @GetMapping("/low-stock-suggestions")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getLowStockSuggestions() {
        return ResponseEntity.ok(ApiResponse.success("Low-stock product suggestions retrieved",
                purchaseOrderService.getLowStockSuggestions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved",
                purchaseOrderService.getPurchaseOrderById(id)));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseRequest(
            @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase request created successfully", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase request created successfully", response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approvePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApprovePurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved and sent to supplier",
                purchaseOrderService.approvePurchaseOrder(id, request)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> rejectPurchaseOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @Valid @RequestBody(required = false) RejectPurchaseOrderRequest request) {
        String effectiveReason = null;
        String managerNote = null;
        if (request != null && request.reason() != null && !request.reason().isBlank()) {
            effectiveReason = request.reason();
            managerNote = request.managerNote();
        } else if (reason != null && !reason.isBlank()) {
            effectiveReason = reason;
        }

        RejectPurchaseOrderRequest finalRequest = new RejectPurchaseOrderRequest(effectiveReason, managerNote);
        return ResponseEntity.ok(ApiResponse.success("Purchase order rejected",
                purchaseOrderService.rejectPurchaseOrder(id, finalRequest)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelPurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order cancelled",
                purchaseOrderService.cancelPurchaseOrder(id)));
    }
}
