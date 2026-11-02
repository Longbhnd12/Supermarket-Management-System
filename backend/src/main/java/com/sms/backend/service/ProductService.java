package com.sms.backend.service;

import com.sms.backend.dto.product.ProductRequest;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.enums.GeneralStatus;

import java.util.List;

/**
 * Service interface for Product operations.
 * TODO (SDD): Confirm stock quantity update rules (direct update vs. via StockMovement only).
 * TODO (SDD): Confirm low-stock threshold trigger behavior.
 */
public interface ProductService {

    List<ProductResponse> getAllProducts();

    List<ProductResponse> getProductsByStatus(GeneralStatus status);

    List<ProductResponse> getProductsByCategory(Long categoryId);

    ProductResponse getProductById(Long id);

    ProductResponse getProductByCode(String productCode);

    ProductResponse getProductByBarcode(String barcode);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
