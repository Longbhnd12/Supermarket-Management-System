package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.product.ProductRequest;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) GeneralStatus status,
            @RequestParam(required = false) Long categoryId) {
        List<ProductResponse> result;
        if (categoryId != null) {
            result = productService.getProductsByCategory(categoryId);
        } else if (status != null) {
            result = productService.getProductsByStatus(status);
        } else {
            result = productService.getAllProducts();
        }
        return ResponseEntity.ok(ApiResponse.success("Products retrieved", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Product retrieved", productService.getProductById(id)));
    }

    @GetMapping("/code/{productCode}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductByCode(@PathVariable String productCode) {
        return ResponseEntity.ok(ApiResponse.success("Product retrieved", productService.getProductByCode(productCode)));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.success("Product retrieved", productService.getProductByBarcode(barcode)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
