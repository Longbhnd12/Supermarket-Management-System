package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.product.PriceHistoryResponse;
import com.sms.backend.dto.product.PriceUpdateRequest;
import com.sms.backend.dto.product.ProductRequest;
import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.SystemLog;
import com.sms.backend.entity.User;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.SystemLogRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SystemLogRepository systemLogRepository;
    private final CurrentUserProvider currentUserProvider;

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
    public ProductResponse updateSellingPrice(Long id, PriceUpdateRequest request) {
        if (request.newSellingPrice() == null || request.newSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Giá bán mới phải lớn hơn hoặc bằng 0");
        }

        Product product = findById(id);
        BigDecimal oldPrice = product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO;
        BigDecimal newPrice = request.newSellingPrice();

        product.setSellingPrice(newPrice);
        Product saved = productRepository.save(product);

        // Record audit change in SystemLog (Sequence Diagram Step 9 & 10)
        try {
            SystemLog log = new SystemLog();
            log.setUser(currentUserProvider.currentUser().orElse(null));
            log.setAction("UPDATE_SELLING_PRICE");
            log.setEntityType("Product");
            log.setEntityId(product.getProductId());

            BigDecimal delta = newPrice.subtract(oldPrice);
            String note = request.reason() != null && !request.reason().isBlank()
                    ? request.reason().trim()
                    : "Standard retail price adjustment";

            log.setDescription(String.format("PRICE_CHANGE|old:%s|new:%s|delta:%s|note:%s",
                    oldPrice.toPlainString(),
                    newPrice.toPlainString(),
                    delta.toPlainString(),
                    note));
            log.setIpAddress(currentUserProvider.clientIp());
            log.setCreatedAt(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception e) {
            // Log audit failure must not block business transaction if table is accessible
        }

        return toResponse(saved);
    }

    @Override
    public List<PriceHistoryResponse> getPriceHistory(Long id) {
        Product product = findById(id);
        List<SystemLog> logs = systemLogRepository.findByEntityTypeAndEntityId("Product", id);

        List<PriceHistoryResponse> history = new ArrayList<>();
        for (SystemLog log : logs) {
            if ("UPDATE_SELLING_PRICE".equalsIgnoreCase(log.getAction())) {
                history.add(parsePriceLog(log));
            }
        }

        // Sort descending by timestamp
        history.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));

        // If no prior adjustment logs, provide initial baseline entry
        if (history.isEmpty()) {
            history.add(new PriceHistoryResponse(
                    0L,
                    product.getSellingPrice(),
                    product.getSellingPrice(),
                    BigDecimal.ZERO,
                    "Hệ thống",
                    "Giá bán khởi tạo ban đầu",
                    LocalDateTime.now().minusDays(14)
            ));
        }

        return history;
    }

    private PriceHistoryResponse parsePriceLog(SystemLog log) {
        BigDecimal oldPrice = BigDecimal.ZERO;
        BigDecimal newPrice = BigDecimal.ZERO;
        BigDecimal delta = BigDecimal.ZERO;
        String note = "Điều chỉnh giá bán";

        String desc = log.getDescription();
        if (desc != null && desc.startsWith("PRICE_CHANGE|")) {
            String[] parts = desc.split("\\|");
            for (String part : parts) {
                if (part.startsWith("old:")) {
                    try { oldPrice = new BigDecimal(part.substring(4)); } catch (Exception ignored) {}
                } else if (part.startsWith("new:")) {
                    try { newPrice = new BigDecimal(part.substring(4)); } catch (Exception ignored) {}
                } else if (part.startsWith("delta:")) {
                    try { delta = new BigDecimal(part.substring(6)); } catch (Exception ignored) {}
                } else if (part.startsWith("note:")) {
                    note = part.substring(5);
                }
            }
        } else if (desc != null) {
            note = desc;
        }

        String changedBy = "Admin";
        if (log.getUser() != null) {
            changedBy = log.getUser().getEmail() != null
                    ? log.getUser().getUsername()
                    : "User #" + log.getUser().getUserId();
        }

        return new PriceHistoryResponse(
                log.getLogId(),
                oldPrice,
                newPrice,
                delta,
                changedBy,
                note,
                log.getCreatedAt() != null ? log.getCreatedAt() : LocalDateTime.now()
        );
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
