package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Sale (POS) operations.
 * TODO (SDD): Confirm authentication requirements – cashier role only vs. admin access.
 * TODO (SDD): Confirm whether invoice number generation is server-side.
 */
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleResponse>>> getAllSales(
            @RequestParam(required = false) SaleStatus status) {
        List<SaleResponse> result = status != null
                ? saleService.getSalesByStatus(status)
                : saleService.getAllSales();
        return ResponseEntity.ok(ApiResponse.success("Sales retrieved", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sale retrieved", saleService.getSaleById(id)));
    }

    @GetMapping("/number/{saleNumber}")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleBySaleNumber(@PathVariable String saleNumber) {
        return ResponseEntity.ok(ApiResponse.success("Sale retrieved", saleService.getSaleBySaleNumber(saleNumber)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> createSale() {
        SaleResponse response = saleService.createSale();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sale created", response));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<SaleResponse>> addItem(
            @PathVariable Long id,
            @Valid @RequestBody SaleItemRequest itemRequest) {
        return ResponseEntity.ok(ApiResponse.success("Item added", saleService.addItemToSale(id, itemRequest)));
    }

    @DeleteMapping("/{id}/items/{saleItemId}")
    public ResponseEntity<ApiResponse<SaleResponse>> removeItem(
            @PathVariable Long id,
            @PathVariable Long saleItemId) {
        return ResponseEntity.ok(ApiResponse.success("Item removed", saleService.removeItemFromSale(id, saleItemId)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SaleResponse>> completeSale(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sale completed", saleService.completeSale(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SaleResponse>> cancelSale(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sale cancelled", saleService.cancelSale(id)));
    }
}
