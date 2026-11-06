package com.sms.backend.config;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("Demo seed skipped because products already exist");
            return;
        }

        Map<String, Category> categories = seedCategories();
        int insertedProducts = seedProducts(categories);
        log.info("Demo seed completed: {} categories available, {} products inserted",
                categories.size(), insertedProducts);
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> existing = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryName, Function.identity(), (first, ignored) -> first));

        List<CategorySeed> seeds = List.of(
                new CategorySeed("Đồ uống", "Beverages", "Soft drinks, bottled water and coffee"),
                new CategorySeed("Thực phẩm", "Food", "Everyday packaged food"),
                new CategorySeed("Chăm sóc cá nhân", "Personal Care", "Personal hygiene and care products"),
                new CategorySeed("Gia dụng", "Household", "Essential household products"),
                new CategorySeed("Sữa và chế phẩm", "Dairy", "Milk, yogurt and dairy products")
        );

        Map<String, Category> result = new LinkedHashMap<>();
        for (CategorySeed seed : seeds) {
            Category category = existing.get(seed.name());
            if (category == null) category = existing.get(seed.legacyName());
            if (category == null) {
                category = new Category();
                category.setCreatedAt(LocalDateTime.now());
            }
            category.setCategoryName(seed.name());
            category.setDescription(seed.description());
            category.setStatus(GeneralStatus.ACTIVE);
            result.put(seed.name(), categoryRepository.save(category));
        }
        return result;
    }

    private int seedProducts(Map<String, Category> categories) {
        List<ProductSeed> seeds = List.of(
                new ProductSeed("Beverages", "DEMO-BEV-001", "8938505974191", "Lavie Mineral Water 500ml", "Bottle", "7000", "84", "20", false),
                new ProductSeed("Beverages", "DEMO-BEV-002", "8934588012225", "Coca-Cola 330ml", "Can", "11000", "12", "20", false),
                new ProductSeed("Beverages", "DEMO-BEV-003", "8936079140028", "Milk Coffee 170ml", "Can", "14000", "0", "15", false),
                new ProductSeed("Food", "DEMO-FOO-001", "8934563138162", "Hao Hao Sour and Spicy Noodles", "Pack", "4500", "120", "30", true),
                new ProductSeed("Food", "DEMO-FOO-002", "8934673570111", "Jasmine Rice 5kg", "Bag", "118000", "18", "10", false),
                new ProductSeed("Food", "DEMO-FOO-003", "8934804031056", "Butter Cookies 300g", "Box", "52000", "7", "12", true),
                new ProductSeed("Personal Care", "DEMO-PER-001", "8935212800157", "Shampoo 650g", "Bottle", "129000", "22", "8", false),
                new ProductSeed("Personal Care", "DEMO-PER-002", "8935001712296", "Toothpaste 180g", "Tube", "39000", "9", "10", false),
                new ProductSeed("Household", "DEMO-HOM-001", "8934868124817", "Dishwashing Liquid 750ml", "Bottle", "36000", "31", "10", false),
                new ProductSeed("Household", "DEMO-HOM-002", "8935030236176", "Facial Tissues 180 Sheets", "Box", "28000", "5", "10", false),
                new ProductSeed("Dairy", "DEMO-DAI-001", "8934673600016", "Unsweetened Fresh Milk 1L", "Carton", "34000", "45", "15", true),
                new ProductSeed("Dairy", "DEMO-DAI-002", "8934673600184", "Sweetened Yogurt 4-Pack", "Pack", "29000", "8", "12", true)
        );

        int inserted = 0;
        for (ProductSeed seed : seeds) {
            Product product = productRepository.findByProductCode(seed.code()).orElse(null);
            if (product == null && productRepository.existsByBarcode(seed.barcode())) {
                continue;
            }
            boolean isNew = product == null;
            if (isNew) product = new Product();
            product.setCategory(categories.get(seed.category()));
            product.setProductCode(seed.code());
            product.setBarcode(seed.barcode());
            product.setProductName(seed.name());
            product.setUnit(seed.unit());
            product.setSellingPrice(new BigDecimal(seed.price()));
            product.setStockQuantity(new BigDecimal(seed.stock()));
            product.setMinimumStockLevel(new BigDecimal(seed.minimumStock()));
            product.setTrackExpiry(seed.trackExpiry());
            product.setStatus(GeneralStatus.ACTIVE);
            productRepository.save(product);
            if (isNew) inserted++;
        }
        return inserted;
    }

    private record ProductSeed(
            String category,
            String code,
            String barcode,
            String name,
            String unit,
            String price,
            String stock,
            String minimumStock,
            boolean trackExpiry
    ) {}

    private record CategorySeed(String legacyName, String name, String description) {}
}
