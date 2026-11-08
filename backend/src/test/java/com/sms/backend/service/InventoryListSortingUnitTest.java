package com.sms.backend.service;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.repository.*;
import com.sms.backend.service.serviceImpl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InventoryListSortingUnitTest {

    private StockReceiptRepository stockReceiptRepository;
    private StockMovementRepository stockMovementRepository;
    private StockCheckRepository stockCheckRepository;
    private ProductRepository productRepository;
    private StockReceiptDetailRepository stockReceiptDetailRepository;
    private SupplierRepository supplierRepository;
    private UserRepository userRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private PurchaseOrderDetailRepository purchaseOrderDetailRepository;
    private SystemSettingRepository systemSettingRepository;
    private NotificationService notificationService;

    private InventoryServiceImpl inventoryService;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        stockReceiptRepository = mock(StockReceiptRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        stockCheckRepository = mock(StockCheckRepository.class);
        productRepository = mock(ProductRepository.class);
        stockReceiptDetailRepository = mock(StockReceiptDetailRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        userRepository = mock(UserRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        purchaseOrderDetailRepository = mock(PurchaseOrderDetailRepository.class);
        systemSettingRepository = mock(SystemSettingRepository.class);
        notificationService = mock(NotificationService.class);

        inventoryService = new InventoryServiceImpl(
                stockReceiptRepository,
                stockMovementRepository,
                stockCheckRepository,
                productRepository,
                stockReceiptDetailRepository,
                supplierRepository,
                userRepository,
                purchaseOrderRepository,
                purchaseOrderDetailRepository,
                systemSettingRepository,
                notificationService
        );

        testCategory = new Category();
        testCategory.setCategoryId(1L);
        testCategory.setCategoryName("Beverages");
    }

    private Product createProduct(Long id, String code, String name) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductCode(code);
        p.setProductName(name);
        p.setCategory(testCategory);
        p.setStockQuantity(new BigDecimal("100.000"));
        p.setMinimumStockLevel(new BigDecimal("10.000"));
        p.setStatus(GeneralStatus.ACTIVE);
        p.setTrackExpiry(false);
        p.setUnit("bottle");
        p.setSellingPrice(new BigDecimal("15000.00"));
        return p;
    }

    @Test
    @DisplayName("UC31 - Default query sorting uses productCode ASC then productId ASC")
    void testDefaultSortingQueryParameters() {
        Product p1 = createProduct(1L, "DEMO-001", "Apple Juice");
        Product p2 = createProduct(2L, "DEMO-002", "Orange Juice");

        when(productRepository.findAllByFilter(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2)));

        InventoryFilter filter = new InventoryFilter(null, null, null, 0, 20);
        InventoryPageResponse response = inventoryService.getInventoryList(filter);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllByFilter(isNull(), isNull(), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        Sort sort = capturedPageable.getSort();
        assertNotNull(sort);

        Sort.Order codeOrder = sort.getOrderFor("productCode");
        assertNotNull(codeOrder, "Primary sort must be on productCode");
        assertEquals(Sort.Direction.ASC, codeOrder.getDirection(), "productCode sort direction must be ASC");

        Sort.Order idOrder = sort.getOrderFor("productId");
        assertNotNull(idOrder, "Secondary sort must be on productId");
        assertEquals(Sort.Direction.ASC, idOrder.getDirection(), "productId sort direction must be ASC");

        assertEquals(2, response.content().size());
        assertEquals("DEMO-001", response.content().get(0).productCode());
        assertEquals("DEMO-002", response.content().get(1).productCode());
    }

    @Test
    @DisplayName("UC31 - Sorting works correctly across multiple pages for DEMO-001..DEMO-030")
    void testSortingAcrossMultiplePages() {
        List<Product> allProducts = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            String code = String.format("DEMO-%03d", i);
            allProducts.add(createProduct((long) i, code, "Product " + i));
        }

        // Simulate page 0 (size 20) -> DEMO-001 to DEMO-020
        List<Product> page0Products = allProducts.subList(0, 20);
        when(productRepository.findAllByFilter(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(2);
                    if (p.getPageNumber() == 0) {
                        return new PageImpl<>(page0Products, p, 30);
                    } else if (p.getPageNumber() == 1) {
                        // Simulate page 1 (size 20) -> DEMO-021 to DEMO-030
                        return new PageImpl<>(allProducts.subList(20, 30), p, 30);
                    }
                    return new PageImpl<>(List.of(), p, 30);
                });

        // Request Page 0
        InventoryFilter page0Filter = new InventoryFilter(null, null, null, 0, 20);
        InventoryPageResponse page0Response = inventoryService.getInventoryList(page0Filter);

        assertEquals(20, page0Response.content().size());
        assertEquals(0, page0Response.page());
        assertEquals("DEMO-001", page0Response.content().get(0).productCode());
        assertEquals("DEMO-020", page0Response.content().get(19).productCode());

        // Verify order on Page 0 is strictly ascending by productCode
        for (int i = 0; i < page0Response.content().size() - 1; i++) {
            String curr = page0Response.content().get(i).productCode();
            String next = page0Response.content().get(i + 1).productCode();
            assertTrue(curr.compareTo(next) < 0, "Page 0 items must be sorted ascending: " + curr + " < " + next);
        }

        // Request Page 1
        InventoryFilter page1Filter = new InventoryFilter(null, null, null, 1, 20);
        InventoryPageResponse page1Response = inventoryService.getInventoryList(page1Filter);

        assertEquals(10, page1Response.content().size());
        assertEquals(1, page1Response.page());
        assertEquals("DEMO-021", page1Response.content().get(0).productCode());
        assertEquals("DEMO-030", page1Response.content().get(9).productCode());

        // Verify continuity across page boundary: Page 0 last item < Page 1 first item
        assertTrue(page0Response.content().get(19).productCode().compareTo(page1Response.content().get(0).productCode()) < 0,
                "DEMO-020 must come before DEMO-021");

        // Verify order on Page 1 is strictly ascending by productCode
        for (int i = 0; i < page1Response.content().size() - 1; i++) {
            String curr = page1Response.content().get(i).productCode();
            String next = page1Response.content().get(i + 1).productCode();
            assertTrue(curr.compareTo(next) < 0, "Page 1 items must be sorted ascending: " + curr + " < " + next);
        }
    }

    @Test
    @DisplayName("UC31 - Stock status filter uses findAllByFilterNoPaging and paginates in productCode ASC order")
    void testStockStatusFilterPaginationOrder() {
        List<Product> sortedList = List.of(
                createProduct(1L, "DEMO-005", "Cabbage"),
                createProduct(2L, "DEMO-010", "Bread"),
                createProduct(3L, "DEMO-011", "Apple Juice"),
                createProduct(4L, "DEMO-013", "Beef Steak"),
                createProduct(5L, "DEMO-026", "Black Pepper")
        );

        when(productRepository.findAllByFilterNoPaging(isNull(), isNull())).thenReturn(sortedList);

        // Page 0, size 3 -> DEMO-005, DEMO-010, DEMO-011
        InventoryFilter filterP0 = new InventoryFilter(null, null, "IN_STOCK", 0, 3);
        InventoryPageResponse resP0 = inventoryService.getInventoryList(filterP0);

        assertEquals(3, resP0.content().size());
        assertEquals("DEMO-005", resP0.content().get(0).productCode());
        assertEquals("DEMO-010", resP0.content().get(1).productCode());
        assertEquals("DEMO-011", resP0.content().get(2).productCode());

        // Page 1, size 3 -> DEMO-013, DEMO-026
        InventoryFilter filterP1 = new InventoryFilter(null, null, "IN_STOCK", 1, 3);
        InventoryPageResponse resP1 = inventoryService.getInventoryList(filterP1);

        assertEquals(2, resP1.content().size());
        assertEquals("DEMO-013", resP1.content().get(0).productCode());
        assertEquals("DEMO-026", resP1.content().get(1).productCode());
    }

    @Test
    @DisplayName("UC31 - Search keyword and category filters are preserved alongside sorting")
    void testKeywordAndCategoryFilterPreserved() {
        Product p = createProduct(10L, "DEMO-010", "Whole Wheat Bread");
        when(productRepository.findAllByFilter(eq("Bread"), eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        InventoryFilter filter = new InventoryFilter("Bread", 5L, null, 0, 20);
        InventoryPageResponse response = inventoryService.getInventoryList(filter);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllByFilter(eq("Bread"), eq(5L), pageableCaptor.capture());

        Pageable pgb = pageableCaptor.getValue();
        assertEquals(Sort.Direction.ASC, pgb.getSort().getOrderFor("productCode").getDirection());
        assertEquals(1, response.content().size());
        assertEquals("DEMO-010", response.content().get(0).productCode());
    }
}

