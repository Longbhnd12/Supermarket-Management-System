package com.sms.backend.service;

import com.sms.backend.dto.inventory.GroupedProductBatchResponse;
import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.ProductBatchResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.entity.User;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockCheckRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.repository.UserRepository;
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

    @Autowired
    private StockReceiptDetailRepository stockReceiptDetailRepository;

    @Autowired
    private StockReceiptRepository stockReceiptRepository;

    @Autowired
    private StockCheckRepository stockCheckRepository;

    @Autowired
    private UserRepository userRepository;

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

    @Test
    @Transactional
    @DisplayName("UC33 - getProductBatches returns list of confirmed batches for product with expiry tracking")
    void testGetProductBatchesSuccess() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        assertFalse(activeBatches.isEmpty(), "Expected at least one active expiring batch in seeded database");
        StockReceiptDetail sample = activeBatches.getFirst();
        Long productId = sample.getProduct().getProductId();

        List<ProductBatchResponse> batches = inventoryService.getProductBatches(productId);
        assertNotNull(batches);
        assertFalse(batches.isEmpty());

        ProductBatchResponse match = batches.stream()
                .filter(b -> b.stockReceiptItemId().equals(sample.getStockReceiptItemId()))
                .findFirst()
                .orElse(null);
        assertNotNull(match);
        assertEquals(sample.getBatchNumber(), match.batchNumber());
        assertEquals(sample.getExpirationDate(), match.expirationDate());
        assertEquals(0, sample.getRemainingQuantity().compareTo(match.remainingQuantity()));
        assertEquals(sample.getQuantity(), match.receivedQuantity());
        assertNotNull(match.stockReceiptId());
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Batch-aware decrease deducts from specific batch and product stock, creating batch-linked StockMovement")
    void testAdjustStockDecreaseWithBatchAllocationSuccess() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        StockReceiptDetail batch = activeBatches.stream()
                .filter(b -> b.getRemainingQuantity().compareTo(new BigDecimal("5")) >= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Need a batch with remainingQty >= 5"));

        Product product = batch.getProduct();
        Long productId = product.getProductId();
        BigDecimal initialProductStock = product.getStockQuantity();
        BigDecimal initialBatchRemaining = batch.getRemainingQuantity();
        BigDecimal adjustQty = new BigDecimal("3");

        StockAdjustmentRequest.BatchAllocationRequest alloc =
                new StockAdjustmentRequest.BatchAllocationRequest(batch.getStockReceiptItemId(), adjustQty);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                productId,
                "DECREASE",
                adjustQty,
                "Damaged units removed from batch",
                null,
                List.of(alloc)
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);

        assertNotNull(response);
        assertEquals("DECREASE", response.adjustmentType());
        assertEquals(0, initialProductStock.subtract(adjustQty).compareTo(response.newStock()));

        // Verify Product entity updated in database
        Product reloadedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, initialProductStock.subtract(adjustQty).compareTo(reloadedProduct.getStockQuantity()));

        // Verify StockReceiptDetail remaining quantity updated in database
        StockReceiptDetail reloadedBatch = stockReceiptDetailRepository.findById(batch.getStockReceiptItemId()).orElseThrow();
        assertEquals(0, initialBatchRemaining.subtract(adjustQty).compareTo(reloadedBatch.getRemainingQuantity()));

        // Verify StockMovement logged with reference to batch item
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement last = movements.getLast();
        assertEquals(StockMovementType.ADJUSTMENT, last.getMovementType());
        assertEquals(0, adjustQty.negate().compareTo(last.getQuantityChange()));
        assertNotNull(last.getStockReceiptDetail());
        assertEquals(batch.getStockReceiptItemId(), last.getStockReceiptDetail().getStockReceiptItemId());
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Batch-aware decrease split across multiple batches updates all batches and logs individual movements")
    void testAdjustStockDecreaseSplitAcrossMultipleBatches() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        assertFalse(activeBatches.isEmpty());
        StockReceiptDetail b1 = activeBatches.getFirst();
        Product targetProduct = b1.getProduct();

        // Ensure b1 has at least 10 remaining
        b1.setRemainingQuantity(new BigDecimal("10"));
        stockReceiptDetailRepository.save(b1);

        // Add a second batch for the same confirmed receipt
        StockReceiptDetail b2 = new StockReceiptDetail();
        b2.setStockReceipt(b1.getStockReceipt());
        b2.setProduct(targetProduct);
        b2.setBatchNumber("BATCH-SPLIT-TEST-002");
        b2.setExpirationDate(java.time.LocalDate.now().plusMonths(8));
        b2.setQuantity(new BigDecimal("10"));
        b2.setRemainingQuantity(new BigDecimal("10"));
        b2.setUnitCost(new BigDecimal("3.00"));
        b2.setSubtotal(new BigDecimal("30.00"));
        b2 = stockReceiptDetailRepository.save(b2);

        targetProduct.setStockQuantity(new BigDecimal("30"));
        productRepository.save(targetProduct);

        BigDecimal b1Initial = b1.getRemainingQuantity();
        BigDecimal b2Initial = b2.getRemainingQuantity();
        BigDecimal pInitial = targetProduct.getStockQuantity();

        BigDecimal alloc1 = new BigDecimal("2");
        BigDecimal alloc2 = new BigDecimal("3");
        BigDecimal totalAdjust = alloc1.add(alloc2);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                targetProduct.getProductId(),
                "DECREASE",
                totalAdjust,
                "Split batch adjustment across two batches",
                null,
                List.of(
                        new StockAdjustmentRequest.BatchAllocationRequest(b1.getStockReceiptItemId(), alloc1),
                        new StockAdjustmentRequest.BatchAllocationRequest(b2.getStockReceiptItemId(), alloc2)
                )
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);

        assertNotNull(response);
        assertEquals(0, pInitial.subtract(totalAdjust).compareTo(response.newStock()));

        // Verify both batches updated
        StockReceiptDetail reloadedB1 = stockReceiptDetailRepository.findById(b1.getStockReceiptItemId()).orElseThrow();
        StockReceiptDetail reloadedB2 = stockReceiptDetailRepository.findById(b2.getStockReceiptItemId()).orElseThrow();
        assertEquals(0, b1Initial.subtract(alloc1).compareTo(reloadedB1.getRemainingQuantity()));
        assertEquals(0, b2Initial.subtract(alloc2).compareTo(reloadedB2.getRemainingQuantity()));

        // Verify 2 StockMovement records created
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement m2 = movements.get(movements.size() - 1);
        StockMovement m1 = movements.get(movements.size() - 2);

        assertEquals(0, alloc2.negate().compareTo(m2.getQuantityChange()));
        assertEquals(b2.getStockReceiptItemId(), m2.getStockReceiptDetail().getStockReceiptItemId());
        assertEquals(0, alloc1.negate().compareTo(m1.getQuantityChange()));
        assertEquals(b1.getStockReceiptItemId(), m1.getStockReceiptDetail().getStockReceiptItemId());
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Over-allocating decrease beyond batch remaining quantity throws BadRequestException")
    void testAdjustStockOverAllocationThrowsBadRequest() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        assertFalse(activeBatches.isEmpty());
        StockReceiptDetail batch = activeBatches.getFirst();
        Product product = batch.getProduct();

        BigDecimal excessiveAlloc = batch.getRemainingQuantity().add(new BigDecimal("100"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                excessiveAlloc,
                "Attempted over-deduction",
                null,
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batch.getStockReceiptItemId(), excessiveAlloc))
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("Insufficient remaining quantity in batch"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Allocation quantity mismatch with total adjustment quantity throws BadRequestException")
    void testAdjustStockAllocationSumMismatchThrowsBadRequest() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        StockReceiptDetail batch = activeBatches.getFirst();
        Product product = batch.getProduct();

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("10"),
                "Mismatched sum test",
                null,
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batch.getStockReceiptItemId(), new BigDecimal("4")))
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("must equal total adjustment quantity"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Missing batch allocations for expiry-tracked product throws BadRequestException")
    void testAdjustStockMissingBatchAllocationForTrackExpiryThrowsBadRequest() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        StockReceiptDetail batch = activeBatches.getFirst();
        Product product = batch.getProduct();

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("2"),
                "Missing allocation test",
                null,
                null
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("Batch allocation is required"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Stock Increase restores quantity to existing batch and product stock")
    void testAdjustStockIncreaseRestoresBatchQuantity() {
        List<StockReceiptDetail> activeBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        StockReceiptDetail batch = activeBatches.getFirst();
        Product product = batch.getProduct();

        BigDecimal initialBatchRemaining = batch.getRemainingQuantity();
        BigDecimal initialProductStock = product.getStockQuantity();
        BigDecimal increaseQty = new BigDecimal("5");

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "INCREASE",
                increaseQty,
                "Found undamaged units returned to batch",
                null,
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batch.getStockReceiptItemId(), increaseQty))
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);

        assertNotNull(response);
        assertEquals("INCREASE", response.adjustmentType());
        assertEquals(0, initialProductStock.add(increaseQty).compareTo(response.newStock()));

        StockReceiptDetail reloadedBatch = stockReceiptDetailRepository.findById(batch.getStockReceiptItemId()).orElseThrow();
        assertEquals(0, initialBatchRemaining.add(increaseQty).compareTo(reloadedBatch.getRemainingQuantity()));

        Product reloadedProduct = productRepository.findById(product.getProductId()).orElseThrow();
        assertEquals(0, initialProductStock.add(increaseQty).compareTo(reloadedProduct.getStockQuantity()));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Linked Stock Check discrepancy reconciliation updates StockCheck status to COMPLETED")
    void testAdjustStockReconcilesLinkedStockCheck() {
        Product product = productRepository.findById(6L).orElseThrow();
        User user = userRepository.findAll().getFirst();

        StockCheck check = new StockCheck();
        check.setCheckNumber("CHK-TEST-001");
        check.setProduct(product);
        check.setCheckedBy(user);
        check.setSystemQuantity(product.getStockQuantity());
        check.setActualQuantity(product.getStockQuantity().subtract(new BigDecimal("3")));
        check.setDifference(new BigDecimal("-3"));
        check.setReason("Shelf audit discrepancy test");
        check.setStatus("PENDING");
        check.setCheckedAt(java.time.LocalDateTime.now());
        StockCheck savedCheck = stockCheckRepository.save(check);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("3"),
                "Reconcile shelf audit discrepancy",
                savedCheck.getStockCheckId()
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);
        assertNotNull(response);
        assertEquals(savedCheck.getStockCheckId(), response.stockCheckId());

        // Verify StockCheck status is now COMPLETED
        StockCheck updatedCheck = stockCheckRepository.findById(savedCheck.getStockCheckId()).orElseThrow();
        assertEquals("COMPLETED", updatedCheck.getStatus());

        // Attempting a second adjustment for the same stock check must be rejected
        StockAdjustmentRequest duplicateReq = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("3"),
                "Attempted duplicate reconciliation",
                savedCheck.getStockCheckId()
        );
        assertThrows(BusinessException.class, () -> inventoryService.adjustStock(duplicateReq));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - getGroupedProductBatches combines confirmed receipt details of the same physical batch")
    void testGroupedProductBatchesCombinesDetailsOfSamePhysicalBatch() {
        Product product = productRepository.findById(1L).orElseThrow();
        StockReceipt receipt = stockReceiptRepository.findAll().stream()
                .filter(r -> r.getStatus() == StockReceiptStatus.CONFIRMED)
                .findFirst()
                .orElseThrow();

        java.time.LocalDate expDate = java.time.LocalDate.now().plusMonths(6);

        // Add 2 receipt details for BATCH-GROUP-001 with same expiration date
        StockReceiptDetail d1 = new StockReceiptDetail();
        d1.setStockReceipt(receipt);
        d1.setProduct(product);
        d1.setBatchNumber("BATCH-GROUP-001");
        d1.setExpirationDate(expDate);
        d1.setQuantity(new BigDecimal("1"));
        d1.setRemainingQuantity(new BigDecimal("1"));
        d1.setUnitCost(new BigDecimal("10000"));
        d1.setSubtotal(new BigDecimal("10000"));
        stockReceiptDetailRepository.save(d1);

        StockReceiptDetail d2 = new StockReceiptDetail();
        d2.setStockReceipt(receipt);
        d2.setProduct(product);
        d2.setBatchNumber("BATCH-GROUP-001");
        d2.setExpirationDate(expDate);
        d2.setQuantity(new BigDecimal("4"));
        d2.setRemainingQuantity(new BigDecimal("4"));
        d2.setUnitCost(new BigDecimal("10500"));
        d2.setSubtotal(new BigDecimal("42000"));
        stockReceiptDetailRepository.save(d2);

        List<GroupedProductBatchResponse> grouped = inventoryService.getGroupedProductBatches(product.getProductId());
        assertNotNull(grouped);

        GroupedProductBatchResponse match = grouped.stream()
                .filter(g -> "BATCH-GROUP-001".equalsIgnoreCase(g.batchNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected to find BATCH-GROUP-001 in grouped batches"));

        assertEquals("BATCH-GROUP-001", match.batchNumber());
        assertEquals(expDate, match.expirationDate());
        assertEquals(0, new BigDecimal("5").compareTo(match.totalRemainingQuantity()));
        assertEquals(2, match.receiptCount());
        assertEquals(2, match.details().size());
        assertFalse(match.conflictingDates());
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Decrease 3 from group containing 1 + 4 units correctly allocates 1 + 2 across receipt details")
    void testAdjustStockDecreaseWithGroupedBatchAllocationDeterministicPolicy() {
        Product product = productRepository.findById(1L).orElseThrow();
        StockReceipt receipt1 = stockReceiptRepository.findAll().stream()
                .filter(r -> r.getStatus() == StockReceiptStatus.CONFIRMED)
                .findFirst()
                .orElseThrow();

        java.time.LocalDate expDate = java.time.LocalDate.now().plusMonths(6);

        // SR0112 (d1): received earlier, 1 unit remaining
        StockReceiptDetail d1 = new StockReceiptDetail();
        d1.setStockReceipt(receipt1);
        d1.setProduct(product);
        d1.setBatchNumber("BATCH-FIFO-001");
        d1.setExpirationDate(expDate);
        d1.setQuantity(new BigDecimal("1"));
        d1.setRemainingQuantity(new BigDecimal("1"));
        d1.setUnitCost(new BigDecimal("10000"));
        d1.setSubtotal(new BigDecimal("10000"));
        d1 = stockReceiptDetailRepository.save(d1);

        // SR0113 (d2): 4 units remaining
        StockReceiptDetail d2 = new StockReceiptDetail();
        d2.setStockReceipt(receipt1);
        d2.setProduct(product);
        d2.setBatchNumber("BATCH-FIFO-001");
        d2.setExpirationDate(expDate);
        d2.setQuantity(new BigDecimal("4"));
        d2.setRemainingQuantity(new BigDecimal("4"));
        d2.setUnitCost(new BigDecimal("10500"));
        d2.setSubtotal(new BigDecimal("42000"));
        d2 = stockReceiptDetailRepository.save(d2);

        BigDecimal initialProductStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
        // Make sure product stock is at least 10 for test
        product.setStockQuantity(initialProductStock.add(new BigDecimal("10")));
        productRepository.save(product);
        BigDecimal currentProductStock = product.getStockQuantity();

        // User decreases 3 units from grouped batch BATCH-FIFO-001
        StockAdjustmentRequest.GroupedBatchAllocationRequest groupAlloc =
                new StockAdjustmentRequest.GroupedBatchAllocationRequest("BATCH-FIFO-001", expDate, new BigDecimal("3"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("3"),
                "Reconcile damaged items from physical batch",
                null,
                null,
                List.of(groupAlloc)
        );

        StockAdjustmentResponse response = inventoryService.adjustStock(request);
        assertNotNull(response);
        assertEquals(0, currentProductStock.subtract(new BigDecimal("3")).compareTo(response.newStock()));

        // Check d1: 1 -> 0
        StockReceiptDetail reloadedD1 = stockReceiptDetailRepository.findById(d1.getStockReceiptItemId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reloadedD1.getRemainingQuantity()));

        // Check d2: 4 -> 2
        StockReceiptDetail reloadedD2 = stockReceiptDetailRepository.findById(d2.getStockReceiptItemId()).orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(reloadedD2.getRemainingQuantity()));

        // Check Product Stock: decreased by exactly 3
        Product reloadedProduct = productRepository.findById(product.getProductId()).orElseThrow();
        assertEquals(0, currentProductStock.subtract(new BigDecimal("3")).compareTo(reloadedProduct.getStockQuantity()));

        // Check Grouped Batch remaining: 5 -> 2
        List<GroupedProductBatchResponse> groupedAfter = inventoryService.getGroupedProductBatches(product.getProductId());
        GroupedProductBatchResponse match = groupedAfter.stream()
                .filter(g -> "BATCH-FIFO-001".equalsIgnoreCase(g.batchNumber()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("2").compareTo(match.totalRemainingQuantity()));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Decrease with grouped allocation exceeding available quantity throws BadRequestException")
    void testAdjustStockDecreaseGroupedExceedingAvailableStockThrowsBadRequest() {
        Product product = productRepository.findById(1L).orElseThrow();
        StockReceipt receipt = stockReceiptRepository.findAll().stream()
                .filter(r -> r.getStatus() == StockReceiptStatus.CONFIRMED)
                .findFirst()
                .orElseThrow();

        java.time.LocalDate expDate = java.time.LocalDate.now().plusMonths(6);

        StockReceiptDetail d1 = new StockReceiptDetail();
        d1.setStockReceipt(receipt);
        d1.setProduct(product);
        d1.setBatchNumber("BATCH-EXCEED-001");
        d1.setExpirationDate(expDate);
        d1.setQuantity(new BigDecimal("2"));
        d1.setRemainingQuantity(new BigDecimal("2"));
        d1.setUnitCost(new BigDecimal("10000"));
        d1.setSubtotal(new BigDecimal("20000"));
        stockReceiptDetailRepository.save(d1);

        StockAdjustmentRequest.GroupedBatchAllocationRequest groupAlloc =
                new StockAdjustmentRequest.GroupedBatchAllocationRequest("BATCH-EXCEED-001", expDate, new BigDecimal("5"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "DECREASE",
                new BigDecimal("5"),
                "Attempted over-deduction from group",
                null,
                null,
                List.of(groupAlloc)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("Insufficient remaining quantity in physical batch"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Stock Increase rejects grouped batch allocations and requires exact receipt detail")
    void testAdjustStockIncreaseRejectsGroupedBatchAllocations() {
        Product product = productRepository.findById(1L).orElseThrow();
        java.time.LocalDate expDate = java.time.LocalDate.now().plusMonths(6);

        StockAdjustmentRequest.GroupedBatchAllocationRequest groupAlloc =
                new StockAdjustmentRequest.GroupedBatchAllocationRequest("BATCH-INC-TEST", expDate, new BigDecimal("2"));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                product.getProductId(),
                "INCREASE",
                new BigDecimal("2"),
                "Invalid grouped increase attempt",
                null,
                null,
                List.of(groupAlloc)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.adjustStock(request)
        );
        assertTrue(ex.getMessage().contains("Specific receipt detail must be selected") || ex.getMessage().contains("Stock increase requires selecting specific receipt details"));
    }

    @Test
    @Transactional
    @DisplayName("UC33 - Same Product + Batch Number with conflicting historical dates are kept separate and flagged")
    void testGroupedProductBatchesDetectsConflictingHistoricalDates() {
        Product product = productRepository.findById(1L).orElseThrow();
        StockReceipt receipt = stockReceiptRepository.findAll().stream()
                .filter(r -> r.getStatus() == StockReceiptStatus.CONFIRMED)
                .findFirst()
                .orElseThrow();

        java.time.LocalDate date1 = java.time.LocalDate.now().plusMonths(6);
        java.time.LocalDate date2 = java.time.LocalDate.now().plusMonths(12);

        StockReceiptDetail d1 = new StockReceiptDetail();
        d1.setStockReceipt(receipt);
        d1.setProduct(product);
        d1.setBatchNumber("BATCH-CONFLICT-TEST");
        d1.setExpirationDate(date1);
        d1.setQuantity(new BigDecimal("3"));
        d1.setRemainingQuantity(new BigDecimal("3"));
        d1.setUnitCost(new BigDecimal("10000"));
        d1.setSubtotal(new BigDecimal("30000"));
        stockReceiptDetailRepository.save(d1);

        StockReceiptDetail d2 = new StockReceiptDetail();
        d2.setStockReceipt(receipt);
        d2.setProduct(product);
        d2.setBatchNumber("BATCH-CONFLICT-TEST");
        d2.setExpirationDate(date2);
        d2.setQuantity(new BigDecimal("4"));
        d2.setRemainingQuantity(new BigDecimal("4"));
        d2.setUnitCost(new BigDecimal("10000"));
        d2.setSubtotal(new BigDecimal("40000"));
        stockReceiptDetailRepository.save(d2);

        List<GroupedProductBatchResponse> grouped = inventoryService.getGroupedProductBatches(product.getProductId());
        List<GroupedProductBatchResponse> matches = grouped.stream()
                .filter(g -> "BATCH-CONFLICT-TEST".equalsIgnoreCase(g.batchNumber()))
                .toList();

        // Must be kept separate as 2 distinct records
        assertEquals(2, matches.size());
        // Both flagged with conflictingDates = true
        assertTrue(matches.get(0).conflictingDates());
        assertTrue(matches.get(1).conflictingDates());
    }
}
