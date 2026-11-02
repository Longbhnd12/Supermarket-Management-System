package com.sms.backend.service;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockMovementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdjustStockIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    @Transactional
    @DisplayName("UC33 - Stock Increase updates inventory stock and logs ADJUSTMENT movement with positive quantity")
    void testAdjustStockIncreaseSuccess() {
        Product product = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
        long initialMovements = stockMovementRepository.count();

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                6L,
                "INCREASE",
                new BigDecimal("15"),
                "Found extra undamaged units during shelf audit",
                null
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);

        assertNotNull(response);
        assertEquals(6L, response.productId());
        assertEquals("INCREASE", response.adjustmentType());
        assertEquals(0, initialStock.compareTo(response.previousStock()));
        assertEquals(0, new BigDecimal("15").compareTo(response.adjustmentQuantity()));
        assertEquals(0, initialStock.add(new BigDecimal("15")).compareTo(response.newStock()));
        assertEquals("Found extra undamaged units during shelf audit", response.reason());
        assertNull(response.stockCheckId());
        assertNotNull(response.adjustedByUsername());
        assertNotNull(response.adjustedAt());

        // Verify Product entity in database
        Product updatedProduct = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("15")).compareTo(updatedProduct.getStockQuantity()));

        // Verify StockMovement logged
        assertEquals(initialMovements + 1, stockMovementRepository.count());
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement last = movements.getLast();
        assertEquals(StockMovementType.ADJUSTMENT, last.getMovementType());
        assertEquals(0, new BigDecimal("15").compareTo(last.getQuantityChange()));
        assertEquals(StockReferenceType.STOCK_CHECK, last.getReferenceType());
        assertEquals(0L, last.getReferenceId());
        assertEquals("Found extra undamaged units during shelf audit", last.getReason());
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Stock Decrease updates inventory stock and logs ADJUSTMENT movement with negative quantity")
    void testAdjustStockDecreaseSuccess() {
        Product product = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                6L,
                "DECREASE",
                new BigDecimal("5"),
                "Water damage during warehouse roof leak",
                null
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);

        assertNotNull(response);
        assertEquals("DECREASE", response.adjustmentType());
        assertEquals(0, initialStock.subtract(new BigDecimal("5")).compareTo(response.newStock()));

        // Verify Product entity
        Product updatedProduct = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock.subtract(new BigDecimal("5")).compareTo(updatedProduct.getStockQuantity()));

        // Verify StockMovement has negative quantity change
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement last = movements.getLast();
        assertEquals(StockMovementType.ADJUSTMENT, last.getMovementType());
        assertEquals(0, new BigDecimal("-5").compareTo(last.getQuantityChange()));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Stock Decrease exceeding on-hand stock throws BadRequestException (prevents negative stock)")
    void testAdjustStockInsufficientStock() {
        Product product = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;

        BigDecimal excessiveDecrease = initialStock.add(new BigDecimal("1000"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                6L,
                "DECREASE",
                excessiveDecrease,
                "Excessive write-off attempt",
                null
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("Insufficient stock"));

        // Verify product stock remains unchanged
        Product reloaded = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock.compareTo(reloaded.getStockQuantity()));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Zero or non-integer adjustment quantity throws BadRequestException")
    void testAdjustStockInvalidQuantity() {
        // Zero quantity
        StockAdjustmentRequest reqZero = new StockAdjustmentRequest(
                6L, "INCREASE", BigDecimal.ZERO, "Zero test", null
        );
        assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(reqZero));

        // Negative quantity in request
        StockAdjustmentRequest reqNeg = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("-5"), "Negative test", null
        );
        assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(reqNeg));

        // Non-integer quantity (e.g. 2.75)
        StockAdjustmentRequest reqDecimal = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("2.75"), "Decimal test", null
        );
        BadRequestException ex = assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(reqDecimal));
        assertTrue(ex.getMessage().contains("positive integer"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Missing or blank reason throws BadRequestException")
    void testAdjustStockMissingReason() {
        // Blank reason
        StockAdjustmentRequest reqBlank = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("5"), "   ", null
        );
        assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(reqBlank));

        // Null reason
        StockAdjustmentRequest reqNull = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("5"), null, null
        );
        assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(reqNull));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Re-adjusting the same Stock Check session throws BusinessException (prevents double update)")
    void testAdjustStockPreventsDuplicateStockCheckAdjustment() {
        Long auditSessionId = 998877L;

        StockAdjustmentRequest request1 = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("2"), "Discrepancy adjustment from check", auditSessionId
        );
        StockAdjustmentResponse resp1 = inventoryService.adjustStock(request1);
        assertNotNull(resp1);
        assertEquals(auditSessionId, resp1.stockCheckId());

        // Second adjustment with identical stockCheckId must fail
        StockAdjustmentRequest request2 = new StockAdjustmentRequest(
                6L, "INCREASE", new BigDecimal("2"), "Attempted duplicate adjustment", auditSessionId
        );
        BusinessException ex = assertThrows(BusinessException.class, () ->
                inventoryService.adjustStock(request2)
        );
        assertTrue(ex.getMessage().contains("already been applied"));
    }

    @Test
    @DisplayName("UC33 - Concurrent stock adjustments execute safely without lost updates")
    void testConcurrentAdjustmentsSafety() throws InterruptedException {
        // Use Product 8 for multi-threaded test
        Product product = productRepository.findById(8L).orElseThrow();
        BigDecimal startingStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;

        int threads = 4;
        BigDecimal adjustPerThread = new BigDecimal("5");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    StockAdjustmentRequest req = new StockAdjustmentRequest(
                            8L,
                            "INCREASE",
                            adjustPerThread,
                            "Concurrent adjustment test #" + idx,
                            null
                    );
                    inventoryService.adjustStock(req);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threads, successCount.get());

        // Verify final stock is exactly startingStock + (threads * 5)
        Product finalProduct = productRepository.findById(8L).orElseThrow();
        BigDecimal expectedStock = startingStock.add(adjustPerThread.multiply(BigDecimal.valueOf(threads)));
        assertEquals(0, expectedStock.compareTo(finalProduct.getStockQuantity()));

        // Roll back the added stock to keep DB clean
        StockAdjustmentRequest revertReq = new StockAdjustmentRequest(
                8L,
                "DECREASE",
                adjustPerThread.multiply(BigDecimal.valueOf(threads)),
                "Cleanup concurrent test stock",
                null
        );
        inventoryService.adjustStock(revertReq);
    }

    @Test
    @Transactional
    @DisplayName("UC33 -> UC31 - Verified stock adjustment immediately reflects in UC31 Inventory List")
    void testAdjustStockReflectedInInventoryList() {
        // Query initial inventory list for product 6 (Milk)
        InventoryFilter filter = new InventoryFilter("PRD-006", null, null, 0, 10);
        InventoryPageResponse initialPage = inventoryService.getInventoryList(filter);
        assertFalse(initialPage.content().isEmpty());
        InventoryResponse initialItem = initialPage.content().getFirst();
        BigDecimal initialStock = initialItem.stockQuantity();

        // Adjust stock: INCREASE by 12
        StockAdjustmentRequest adjustReq = new StockAdjustmentRequest(
                6L,
                "INCREASE",
                new BigDecimal("12"),
                "Reconcile shelf overstock",
                null
        );
        StockAdjustmentResponse adjustRes = inventoryService.adjustStock(adjustReq);
        assertEquals(0, initialStock.add(new BigDecimal("12")).compareTo(adjustRes.newStock()));

        // Query UC31 Inventory list again
        InventoryPageResponse updatedPage = inventoryService.getInventoryList(filter);
        assertFalse(updatedPage.content().isEmpty());
        InventoryResponse updatedItem = updatedPage.content().getFirst();

        // Verify that UC31 inventory response contains the new adjusted stock
        assertEquals(0, initialStock.add(new BigDecimal("12")).compareTo(updatedItem.stockQuantity()));
    }
}
