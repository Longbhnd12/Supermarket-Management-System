package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Purchase Order operations.
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getAllPurchaseOrders(
            @RequestParam(required = false) PurchaseOrderStatus status) {
        List<PurchaseOrderResponse> result = status != null
                ? purchaseOrderService.getPurchaseOrdersByStatus(status)
                : purchaseOrderService.getAllPurchaseOrders();
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved", result));
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
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approvePurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved",
                purchaseOrderService.approvePurchaseOrder(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> rejectPurchaseOrder(
            @PathVariable Long id,
            @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order rejected",
                purchaseOrderService.rejectPurchaseOrder(id, reason)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelPurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order cancelled",
                purchaseOrderService.cancelPurchaseOrder(id)));
    }
}
