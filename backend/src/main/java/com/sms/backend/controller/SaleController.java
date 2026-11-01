package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SaleListItemResponse;
import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.dto.sale.CreateSaleRequest;
import com.sms.backend.dto.sale.SaleProductResponse;
import com.sms.backend.dto.sale.UpdateSaleItemRequest;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for Sale (POS) operations.
 * TODO (SDD): Confirm authentication requirements – cashier role only vs. admin access.
 * UC30 assigns invoice numbers on the server after successful payment.
 */
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    /** UC21 - lists every business status, including DRAFT (shown as Pending in the UI). */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SaleListItemResponse>>> getSales(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Sales retrieved successfully", saleService.getSales(search, status, fromDate, toDate, page, size)
        ));
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
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(@Valid @RequestBody CreateSaleRequest request) {
        SaleResponse response = saleService.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sale created", response));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<SaleProductResponse>>> searchProducts(
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success("Products retrieved",
                saleService.searchProducts(barcode, keyword)));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<SaleResponse>> addItem(
            @PathVariable Long id, @Valid @RequestBody SaleItemRequest itemRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Item added", saleService.addItemToSale(id, itemRequest)));
    }

    @PatchMapping("/{id}/items/{saleItemId}")
    public ResponseEntity<ApiResponse<SaleResponse>> updateItemQuantity(
            @PathVariable Long id, @PathVariable Long saleItemId,
            @Valid @RequestBody UpdateSaleItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Item quantity updated",
                saleService.updateItemQuantity(id, saleItemId, request)));
    }

    @DeleteMapping("/{id}/items/{saleItemId}")
    public ResponseEntity<ApiResponse<SaleResponse>> removeItem(@PathVariable Long id, @PathVariable Long saleItemId) {
        return ResponseEntity.ok(ApiResponse.success("Item removed", saleService.removeItemFromSale(id, saleItemId)));
    }

    @PostMapping("/{id}/calculate")
    public ResponseEntity<ApiResponse<SaleResponse>> calculateSaleTotal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sale totals calculated", saleService.calculateSaleTotal(id)));
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
