package com.sms.backend.config;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DemoDataInitializerUnitTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private DemoDataInitializer demoDataInitializer;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        demoDataInitializer = new DemoDataInitializer(categoryRepository, productRepository);
    }

    @Test
    @DisplayName("DemoDataInitializer seeds products with strictly 0 stock quantity (no unbacked nonzero stock)")
    void testDemoDataSeedsZeroStockQuantity() {
        when(productRepository.count()).thenReturn(0L);
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findByProductCode(any())).thenReturn(Optional.empty());

        demoDataInitializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, atLeastOnce()).save(captor.capture());

        List<Product> savedProducts = captor.getAllValues();
        assertNotNull(savedProducts);
        // All seeded products must have stockQuantity == 0 (zero unbacked inventory)
        for (Product p : savedProducts) {
            assertEquals(0, BigDecimal.ZERO.compareTo(p.getStockQuantity()),
                    "Product " + p.getProductCode() + " was seeded with non-zero stock: " + p.getStockQuantity());
        }
    }

    @Test
    @DisplayName("DemoDataInitializer skips execution when products already exist in database")
    void testDemoDataSkipsWhenProductsExist() {
        when(productRepository.count()).thenReturn(10L);

        demoDataInitializer.run(new DefaultApplicationArguments(new String[0]));

        verify(categoryRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }
}

