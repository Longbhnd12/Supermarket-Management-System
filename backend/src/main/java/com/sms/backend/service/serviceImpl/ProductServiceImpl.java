package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.product.ProductRequest;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> getProductsByStatus(GeneralStatus status) {
        return productRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        return productRepository.findByCategory(category)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getProductById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public ProductResponse getProductByCode(String productCode) {
        return productRepository.findByProductCode(productCode)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with code: " + productCode));
    }

    @Override
    public ProductResponse getProductByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with barcode: " + barcode));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        // TODO: Implement – validate uniqueness, resolve category, create Product
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        // TODO: Implement – update Product fields
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        // TODO (SDD): Confirm deletion policy (soft vs hard, guard against active sales)
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getProductId(),
                p.getCategory().getCategoryId(),
                p.getCategory().getCategoryName(),
                p.getProductCode(),
                p.getBarcode(),
                p.getProductName(),
                p.getUnit(),
                p.getSellingPrice(),
                p.getStockQuantity(),
                p.getMinimumStockLevel(),
                p.getTrackExpiry(),
                p.getStatus()
        );
    }
}
