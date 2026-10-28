package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for inventory operations.
 * Covers StockReceipt, StockMovement, and StockCheck.
 * TODO (SDD): Confirm StockReceipt creation endpoint and request body structure.
 * TODO (SDD): Confirm StockCheck creation and adjustment workflow endpoints.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // --- Stock Receipts ---

    @GetMapping("/receipts")
    public ResponseEntity<ApiResponse<List<StockReceiptResponse>>> getAllReceipts(
            @RequestParam(required = false) StockReceiptStatus status) {
        List<StockReceiptResponse> result = status != null
                ? inventoryService.getStockReceiptsByStatus(status)
                : inventoryService.getAllStockReceipts();
        return ResponseEntity.ok(ApiResponse.success("Stock receipts retrieved", result));
    }

    @GetMapping("/receipts/{id}")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> getReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock receipt retrieved",
                inventoryService.getStockReceiptById(id)));
    }

    @PostMapping("/receipts/{id}/confirm")
    public ResponseEntity<ApiResponse<StockReceiptResponse>> confirmReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock receipt confirmed",
                inventoryService.confirmStockReceipt(id)));
    }

    // --- Stock Movements ---

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) StockReferenceType referenceType,
            @RequestParam(required = false) Long referenceId) {

        List<StockMovementResponse> result;
        if (productId != null) {
            result = inventoryService.getMovementsByProduct(productId);
        } else if (referenceType != null && referenceId != null) {
            result = inventoryService.getMovementsByReference(referenceType, referenceId);
        } else if (movementType != null) {
            result = inventoryService.getMovementsByType(movementType);
        } else {
            // TODO (SDD): Confirm whether a global movements list is required
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved", result));
    }

    // --- Stock Checks ---

    @GetMapping("/checks")
    public ResponseEntity<ApiResponse<List<StockCheckResponse>>> getAllChecks(
            @RequestParam(required = false) Long productId) {
        List<StockCheckResponse> result = productId != null
                ? inventoryService.getStockChecksByProduct(productId)
                : inventoryService.getAllStockChecks();
        return ResponseEntity.ok(ApiResponse.success("Stock checks retrieved", result));
    }

    @GetMapping("/checks/{id}")
    public ResponseEntity<ApiResponse<StockCheckResponse>> getCheckById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stock check retrieved",
                inventoryService.getStockCheckById(id)));
    }
}
