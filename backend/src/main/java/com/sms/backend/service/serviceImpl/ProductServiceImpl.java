package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.product.ProductRequest;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        String productCode = request.productCode().trim();
        if (productRepository.existsByProductCode(productCode)) {
            throw new BadRequestException("SKU code already exists: " + productCode);
        }

        String barcode = request.barcode() != null && !request.barcode().isBlank()
                ? request.barcode().trim() : null;
        if (barcode != null && productRepository.existsByBarcode(barcode)) {
            throw new BadRequestException("Barcode already exists: " + barcode);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        Product product = new Product();
        product.setCategory(category);
        product.setProductCode(productCode);
        product.setBarcode(barcode);
        product.setProductName(request.productName().trim());
        product.setUnit(request.unit().trim());
        product.setSellingPrice(request.sellingPrice());
        product.setStockQuantity(BigDecimal.ZERO);
        product.setMinimumStockLevel(request.minimumStockLevel() != null ? request.minimumStockLevel() : BigDecimal.ZERO);
        product.setTrackExpiry(Boolean.TRUE.equals(request.trackExpiry()));
        product.setStatus(GeneralStatus.ACTIVE);

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findById(id);

        String productCode = request.productCode().trim();
        if (productRepository.existsByProductCodeAndProductIdNot(productCode, id)) {
            throw new BadRequestException("SKU code already exists: " + productCode);
        }

        String barcode = request.barcode() != null && !request.barcode().isBlank()
                ? request.barcode().trim() : null;
        if (barcode != null && productRepository.existsByBarcodeAndProductIdNot(barcode, id)) {
            throw new BadRequestException("Barcode already exists: " + barcode);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        product.setCategory(category);
        product.setProductCode(productCode);
        product.setBarcode(barcode);
        product.setProductName(request.productName().trim());
        product.setUnit(request.unit().trim());
        if (request.sellingPrice() != null) {
            product.setSellingPrice(request.sellingPrice());
        }
        if (request.minimumStockLevel() != null) {
            product.setMinimumStockLevel(request.minimumStockLevel());
        }
        if (request.trackExpiry() != null) {
            product.setTrackExpiry(request.trackExpiry());
        }

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findById(id);
        // Soft-deactivation to preserve historical sales & inventory logs
        product.setStatus(GeneralStatus.INACTIVE);
        productRepository.save(product);
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
