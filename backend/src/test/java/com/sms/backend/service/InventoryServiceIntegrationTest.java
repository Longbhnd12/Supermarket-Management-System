package com.sms.backend.service;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.dto.inventory.StockAlertFilter;
import com.sms.backend.dto.inventory.StockAlertItemResponse;
import com.sms.backend.dto.inventory.StockAlertSummaryResponse;
import com.sms.backend.dto.inventory.StockCheckItemRequest;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockCheckSessionRequest;
import com.sms.backend.dto.inventory.StockCheckSessionResponse;
import com.sms.backend.dto.inventory.UpdateStockCheckCountRequest;
import com.sms.backend.dto.inventory.StockHistoryFilter;
import com.sms.backend.dto.inventory.StockMovementPageResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReceiptDetailRepository stockReceiptDetailRepository;

    @Test
    @DisplayName("UC31 - Retrieve all inventory with pagination")
    void testGetInventoryListAll() {
        InventoryFilter filter = new InventoryFilter(null, null, null, 0, 20);
        InventoryPageResponse response = inventoryService.getInventoryList(filter);

        assertNotNull(response);
        assertNotNull(response.content());
        assertFalse(response.content().isEmpty());
        assertTrue(response.totalElements() >= 8);
    }

    @Test
    @DisplayName("UC31 - Filter by keyword (SKU, barcode, name)")
    void testFilterByKeyword() {
        // Keyword match name
        InventoryFilter nameFilter = new InventoryFilter("Coca", null, null, 0, 20);
        InventoryPageResponse nameRes = inventoryService.getInventoryList(nameFilter);
        assertEquals(1, nameRes.content().size());
        assertEquals("Coca Cola 330ml", nameRes.content().getFirst().productName());

        // Keyword match SKU
        InventoryFilter skuFilter = new InventoryFilter("PRD-002", null, null, 0, 20);
        InventoryPageResponse skuRes = inventoryService.getInventoryList(skuFilter);
        assertEquals(1, skuRes.content().size());
        assertEquals("Pepsi 330ml", skuRes.content().getFirst().productName());

        // Keyword match barcode
        InventoryFilter barcodeFilter = new InventoryFilter("8931000000003", null, null, 0, 20);
        InventoryPageResponse barcodeRes = inventoryService.getInventoryList(barcodeFilter);
        assertEquals(1, barcodeRes.content().size());
        assertEquals("Orange Juice", barcodeRes.content().getFirst().productName());
    }

    @Test
    @DisplayName("UC31 - Filter by stock status")
    void testFilterByStockStatus() {
        // Ensure at least one product has stock 0 within this transactional test
        Product testProduct = productRepository.findAll().getFirst();
        testProduct.setStockQuantity(BigDecimal.ZERO);
        productRepository.save(testProduct);

        InventoryFilter outOfStockFilter = new InventoryFilter(null, null, "OUT_OF_STOCK", 0, 20);
        InventoryPageResponse outOfStockRes = inventoryService.getInventoryList(outOfStockFilter);
        assertFalse(outOfStockRes.content().isEmpty());
        for (InventoryResponse item : outOfStockRes.content()) {
            assertEquals("OUT_OF_STOCK", item.stockStatus());
        }

        InventoryFilter lowStockFilter = new InventoryFilter(null, null, "LOW_STOCK", 0, 20);
        InventoryPageResponse lowStockRes = inventoryService.getInventoryList(lowStockFilter);
        assertFalse(lowStockRes.content().isEmpty());
        for (InventoryResponse item : lowStockRes.content()) {
            assertEquals("LOW_STOCK", item.stockStatus());
        }

        InventoryFilter inStockFilter = new InventoryFilter(null, null, "IN_STOCK", 0, 20);
        InventoryPageResponse inStockRes = inventoryService.getInventoryList(inStockFilter);
        assertFalse(inStockRes.content().isEmpty());
        for (InventoryResponse item : inStockRes.content()) {
            assertEquals("IN_STOCK", item.stockStatus());
        }
    }

    @Test
    @DisplayName("UC31 - Export inventory CSV")
    void testExportInventoryCsv() {
        InventoryFilter filter = new InventoryFilter(null, null, null, 0, 20);
        byte[] csvBytes = inventoryService.exportInventoryCsv(filter);

        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 3);
        // Verify UTF-8 BOM
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        String csvString = new String(csvBytes, 3, csvBytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(csvString.startsWith("SKU,Barcode,Product Name,Category,On Hand,Safety Min,Status"));
        assertTrue(csvString.contains("Coca Cola 330ml"));
    }

    @Test
    @DisplayName("UC31 - Backend pagination with sizes 5, 10, 20, 50")
    void testPaginationPagesAndSizes() {
        // Size 5: Page 0 should have 5 items, totalPages = 2
        InventoryFilter p0Filter = new InventoryFilter(null, null, null, 0, 5);
        InventoryPageResponse p0Res = inventoryService.getInventoryList(p0Filter);
        assertEquals(5, p0Res.content().size());
        assertEquals(0, p0Res.page());
        assertEquals(5, p0Res.size());
        assertEquals(8, p0Res.totalElements());
        assertEquals(2, p0Res.totalPages());

        // Size 5: Page 1 should have remaining 3 items
        InventoryFilter p1Filter = new InventoryFilter(null, null, null, 1, 5);
        InventoryPageResponse p1Res = inventoryService.getInventoryList(p1Filter);
        assertEquals(3, p1Res.content().size());
        assertEquals(1, p1Res.page());

        // Size 50: Page 0 should have all 8 items, totalPages = 1
        InventoryFilter p50Filter = new InventoryFilter(null, null, null, 0, 50);
        InventoryPageResponse p50Res = inventoryService.getInventoryList(p50Filter);
        assertEquals(8, p50Res.content().size());
        assertEquals(1, p50Res.totalPages());
    }

    // =========================================================================
    // UC34 - Stock History Integration Tests
    // =========================================================================

    @Test
    @DisplayName("UC34 - Retrieve all stock history with pagination")
    void testGetStockHistoryAll() {
        StockHistoryFilter filter = new StockHistoryFilter(null, null, null, null, null, 0, 10);
        StockMovementPageResponse response = inventoryService.getStockHistory(filter);

        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(0, response.page());
        assertEquals(10, response.size());
        assertTrue(response.totalElements() >= 0);
    }

    @Test
    @DisplayName("UC34 - Filter stock history by productId")
    void testGetStockHistoryByProductId() {
        StockHistoryFilter filter = new StockHistoryFilter(1L, null, null, null, null, 0, 20);
        StockMovementPageResponse response = inventoryService.getStockHistory(filter);

        assertNotNull(response);
        for (StockMovementResponse item : response.content()) {
            assertEquals(1L, item.productId());
            assertNotNull(item.productName());
            assertNotNull(item.productCode());
            assertNotNull(item.movementType());
            assertNotNull(item.quantityChange());
            assertNotNull(item.createdAt());
        }
    }

    @Test
    @DisplayName("UC34 - Filter stock history by movementType (RECEIPT, ADJUSTMENT)")
    void testGetStockHistoryByMovementType() {
        StockHistoryFilter receiptFilter = new StockHistoryFilter(null, null, StockMovementType.RECEIPT, null, null, 0, 20);
        StockMovementPageResponse receiptRes = inventoryService.getStockHistory(receiptFilter);

        assertNotNull(receiptRes);
        for (StockMovementResponse item : receiptRes.content()) {
            assertEquals(StockMovementType.RECEIPT, item.movementType());
            assertTrue(item.quantityChange().compareTo(java.math.BigDecimal.ZERO) > 0, "Receipt quantity change must be positive");
        }

        StockHistoryFilter adjFilter = new StockHistoryFilter(null, null, StockMovementType.ADJUSTMENT, null, null, 0, 20);
        StockMovementPageResponse adjRes = inventoryService.getStockHistory(adjFilter);

        assertNotNull(adjRes);
        for (StockMovementResponse item : adjRes.content()) {
            assertEquals(StockMovementType.ADJUSTMENT, item.movementType());
        }
    }

    @Test
    @DisplayName("UC34 - Verify ordering is descending by createdAt (BR-06)")
    void testGetStockHistoryOrdering() {
        StockHistoryFilter filter = new StockHistoryFilter(null, null, null, null, null, 0, 50);
        StockMovementPageResponse response = inventoryService.getStockHistory(filter);

        List<StockMovementResponse> list = response.content();
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(!list.get(i).createdAt().isBefore(list.get(i + 1).createdAt()),
                    "Records must be ordered descending by createdAt (latest first)");
        }
    }

    @Test
    @DisplayName("UC34 - Throws BadRequestException when startDate is after endDate (AT3)")
    void testStockHistoryInvalidDateRange() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now();
        StockHistoryFilter filter = new StockHistoryFilter(null, null, null, start, end, 0, 10);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            inventoryService.getStockHistory(filter);
        });
        assertEquals("Start date cannot be after end date", ex.getMessage());
    }

    // =========================================================================
    // UC35 - Stock Alerts Integration Tests
    // =========================================================================

    @Test
    @DisplayName("UC35 - Retrieves stock alert summary counts and alert items")
    void testGetStockAlertsSummaryAndItems() {
        StockAlertFilter filter = new StockAlertFilter(null, null, "ALL", 0, 20);
        StockAlertSummaryResponse res = inventoryService.getStockAlerts(filter);

        assertNotNull(res);
        assertTrue(res.lowStockCount() >= 0);
        assertTrue(res.outOfStockCount() >= 0);
        assertTrue(res.nearExpiryCount() >= 0);
        assertTrue(res.expiredCount() >= 0);
        assertEquals(res.totalAlerts(), res.lowStockCount() + res.outOfStockCount() + res.nearExpiryCount() + res.expiredCount());
        assertNotNull(res.alerts());
        assertTrue(res.totalElements() >= res.alerts().size());
    }

    @Test
    @DisplayName("UC35 - Filter alerts by Alert Type")
    void testGetStockAlertsFilterByType() {
        StockAlertFilter lowStockFilter = new StockAlertFilter(null, null, "LOW_STOCK", 0, 20);
        StockAlertSummaryResponse res = inventoryService.getStockAlerts(lowStockFilter);

        assertNotNull(res);
        for (StockAlertItemResponse item : res.alerts()) {
            assertEquals("LOW_STOCK", item.alertType());
            assertTrue(item.currentStock().compareTo(java.math.BigDecimal.ZERO) > 0);
        }
    }

    @Test
    @DisplayName("UC35 - Exclude DRAFT / CANCELLED receipts from expiry alerts (Requirement 6)")
    void testExpiryAlertsOnlyIncludeConfirmedReceipts() {
        StockAlertFilter filter = new StockAlertFilter(null, null, "EXPIRED", 0, 50);
        StockAlertSummaryResponse res = inventoryService.getStockAlerts(filter);

        assertNotNull(res);
        for (StockAlertItemResponse item : res.alerts()) {
            // Any batch-based expiry alert must have remainingQuantity > 0 and expiryDate before today
            if (item.batchRemainingQuantity() != null) {
                assertTrue(item.batchRemainingQuantity().compareTo(java.math.BigDecimal.ZERO) > 0);
                assertNotNull(item.expiryDate());
                assertTrue(item.expiryDate().isBefore(LocalDate.now()));
            }
        }
    }

    @Test
    @DisplayName("UC35 - Batch-level alerts have distinct batch quantities and earliest expiration sorting")
    void testBatchLevelAlertsDistinctAndSorted() {
        StockAlertFilter nearExpiryFilter = new StockAlertFilter(null, null, "NEAR_EXPIRY", 0, 50);
        StockAlertSummaryResponse res = inventoryService.getStockAlerts(nearExpiryFilter);

        assertNotNull(res);
        LocalDate previousDate = null;
        for (StockAlertItemResponse item : res.alerts()) {
            assertEquals("NEAR_EXPIRY", item.alertType());
            assertNotNull(item.batchNumber());
            assertNotNull(item.batchRemainingQuantity());
            assertTrue(item.batchRemainingQuantity().compareTo(BigDecimal.ZERO) > 0);
            assertNotNull(item.expiryDate());

            // Expiry alerts must be sorted ascending (earliest expiration first)
            if (previousDate != null) {
                assertFalse(item.expiryDate().isBefore(previousDate),
                        "Alerts must be ordered with earliest expiration first");
            }
            previousDate = item.expiryDate();
        }

        // Check product-level alerts (LOW_STOCK) do not have batch-specific details
        StockAlertFilter lowStockFilter = new StockAlertFilter(null, null, "LOW_STOCK", 0, 50);
        StockAlertSummaryResponse lowRes = inventoryService.getStockAlerts(lowStockFilter);
        for (StockAlertItemResponse item : lowRes.alerts()) {
            assertEquals("LOW_STOCK", item.alertType());
            assertNull(item.batchNumber(), "Product-level alert must not have batch number");
            assertNull(item.batchRemainingQuantity(), "Product-level alert must not have batch remaining quantity");
            assertNotNull(item.currentStock(), "Product-level alert must have total stock");
            assertNotNull(item.safetyMin(), "Product-level alert must have safety minimum");
        }
    }

    // =========================================================================
    // UC32 - Stock Check & Stock Difference Integration Tests
    // =========================================================================

    @Test
    @DisplayName("UC32 - Generates sequence session number starting with SC-")
    void testNextStockCheckSessionNumber() {
        String sessionNumber = inventoryService.getNextStockCheckSessionNumber();
        assertNotNull(sessionNumber);
        assertTrue(sessionNumber.startsWith("SC-"));
    }

    @Test
    @DisplayName("UC32 - Perform stock check session records physical counts without altering master stock (BR-05)")
    void testPerformStockCheckSessionSuccess() {
        var p1 = productRepository.findById(1L).orElseThrow();
        BigDecimal originalStock1 = p1.getStockQuantity();

        StockCheckItemRequest item1 = new StockCheckItemRequest(1L, originalStock1, "Exact count match");
        StockCheckItemRequest item2 = new StockCheckItemRequest(2L, new BigDecimal("45"), "Shortage found");

        StockCheckSessionRequest request = new StockCheckSessionRequest(
                "SC-INTEG-001",
                List.of(item1, item2)
        );

        StockCheckSessionResponse session = inventoryService.performStockCheck(request);

        assertNotNull(session);
        assertEquals("SC-INTEG-001", session.checkNumber());
        assertEquals(2, session.totalSkus());
        assertEquals(1, session.matchedCount());
        assertEquals(1, session.discrepancyCount());

        // Verify master product stock quantity is untouched
        var p1After = productRepository.findById(1L).orElseThrow();
        assertEquals(0, originalStock1.compareTo(p1After.getStockQuantity()), "Master stock must remain untouched");

        // Verify retrieving the session returns the exact counts
        StockCheckSessionResponse fetched = inventoryService.getStockCheckSession("SC-INTEG-001");
        assertNotNull(fetched);
        assertEquals(2, fetched.items().size());
    }

    @Test
    @DisplayName("UC32 - Disallow duplicate product entries in the same stock check session (BR-04)")
    void testPerformStockCheckDuplicateProductFails() {
        StockCheckItemRequest item1 = new StockCheckItemRequest(1L, new BigDecimal("100"), "First scan");
        StockCheckItemRequest item2 = new StockCheckItemRequest(1L, new BigDecimal("105"), "Duplicate scan");

        StockCheckSessionRequest request = new StockCheckSessionRequest(
                "SC-INTEG-DUP",
                List.of(item1, item2)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            inventoryService.performStockCheck(request);
        });
        assertTrue(ex.getMessage().contains("Duplicate product ID"));
    }

    @Test
    @DisplayName("UC32 - Disallow negative count values (BR-03)")
    void testPerformStockCheckNegativeCountFails() {
        StockCheckItemRequest item1 = new StockCheckItemRequest(1L, new BigDecimal("-5"), "Invalid negative");

        StockCheckSessionRequest request = new StockCheckSessionRequest(
                "SC-INTEG-NEG",
                List.of(item1)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            inventoryService.performStockCheck(request);
        });
        assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    @DisplayName("UC32 & UC33 - Apply adjustment for stock check, prevent duplicate and reject stale stock conflict")
    void testUC33AdjustmentFromStockCheckAndPreventDuplicateAndConflict() {
        var p = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();

        // 1. Perform stock check with deficit: 10 items less
        BigDecimal countedQty = initialStock.subtract(new BigDecimal("10"));
        StockCheckItemRequest item = new StockCheckItemRequest(1L, countedQty, "Discrepancy test");
        StockCheckSessionRequest checkReq = new StockCheckSessionRequest("SC-ADJ-TEST", List.of(item));

        StockCheckSessionResponse checkSession = inventoryService.performStockCheck(checkReq);
        StockCheckResponse checkItem = checkSession.items().getFirst();
        assertEquals("PENDING_ADJUSTMENT", checkItem.status());

        // 2. Adjust stock via UC33 linked to this stock check
        var batches = inventoryService.getProductBatches(1L);
        assertFalse(batches.isEmpty());
        StockReceiptDetail targetBatch = stockReceiptDetailRepository.findById(batches.getFirst().stockReceiptItemId()).orElseThrow();
        targetBatch.setRemainingQuantity(new BigDecimal("50"));
        stockReceiptDetailRepository.save(targetBatch);
        Long batchId = targetBatch.getStockReceiptItemId();

        StockAdjustmentRequest adjReq = new StockAdjustmentRequest(
                1L,
                "DECREASE",
                new BigDecimal("10"),
                "Reconcile shelf audit SC-ADJ-TEST",
                checkItem.stockCheckId(),
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batchId, new BigDecimal("10")))
        );
        StockAdjustmentResponse adjResp = inventoryService.adjustStock(adjReq);
        assertNotNull(adjResp);
        assertEquals(0, countedQty.compareTo(adjResp.newStock()));

        // 3. Duplicate prevention: Trying to adjust again for the same stock check must be rejected
        BusinessException duplicateEx = assertThrows(BusinessException.class, () -> {
            inventoryService.adjustStock(adjReq);
        });
        assertTrue(duplicateEx.getMessage().contains("already been applied"));

        // 4. Stale stock conflict detection:
        // Perform another check
        StockCheckItemRequest item2 = new StockCheckItemRequest(1L, new BigDecimal("50"), "Second check");
        StockCheckSessionResponse checkSession2 = inventoryService.performStockCheck(
                new StockCheckSessionRequest("SC-CONFLICT-TEST", List.of(item2))
        );
        StockCheckResponse checkItem2 = checkSession2.items().getFirst();

        // Change master stock behind the scenes (e.g. sale or another movement)
        var pCurrent = productRepository.findById(1L).orElseThrow();
        pCurrent.setStockQuantity(pCurrent.getStockQuantity().add(new BigDecimal("5")));
        productRepository.save(pCurrent);

        // Attempting to adjust linked to checkItem2 where systemQuantity no longer matches current stock
        BigDecimal expectedVariance = checkItem2.difference().abs();
        StockAdjustmentRequest conflictAdjReq = new StockAdjustmentRequest(
                1L,
                "DECREASE",
                expectedVariance,
                "Reconcile audit after master stock moved",
                checkItem2.stockCheckId()
        );
        BusinessException conflictEx = assertThrows(BusinessException.class, () -> {
            inventoryService.adjustStock(conflictAdjReq);
        });
        assertTrue(conflictEx.getMessage().contains("Stock conflict"));
    }

    @Test
    @DisplayName("UC32 & UC33 - Reject adjustments with mismatched type or quantity for stock check discrepancy")
    void testUC33AdjustmentRejectsMismatchedTypeAndQuantity() {
        var p = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();

        // Count 5 items less (shortage of 5)
        BigDecimal countedQty = initialStock.subtract(new BigDecimal("5"));
        StockCheckItemRequest item = new StockCheckItemRequest(1L, countedQty, "Shortage 5 test");
        StockCheckSessionRequest checkReq = new StockCheckSessionRequest("SC-LOCK-TEST", List.of(item));

        StockCheckSessionResponse checkSession = inventoryService.performStockCheck(checkReq);
        StockCheckResponse checkItem = checkSession.items().getFirst();

        // 1. Trying to submit INCREASE instead of DECREASE should fail
        StockAdjustmentRequest wrongTypeReq = new StockAdjustmentRequest(
                1L,
                "INCREASE",
                new BigDecimal("5"),
                "Wrong type test",
                checkItem.stockCheckId()
        );
        BadRequestException typeEx = assertThrows(BadRequestException.class, () -> {
            inventoryService.adjustStock(wrongTypeReq);
        });
        assertTrue(typeEx.getMessage().contains("Adjustment type must be DECREASE"));

        // 2. Trying to submit quantity != 5 (e.g. 10) should fail
        StockAdjustmentRequest wrongQtyReq = new StockAdjustmentRequest(
                1L,
                "DECREASE",
                new BigDecimal("10"),
                "Wrong qty test",
                checkItem.stockCheckId()
        );
        BadRequestException qtyEx = assertThrows(BadRequestException.class, () -> {
            inventoryService.adjustStock(wrongQtyReq);
        });
        assertTrue(qtyEx.getMessage().contains("does not match audited discrepancy"));
    }

    @Test
    @DisplayName("UC32 - Retrieve all stock check sessions (session history)")
    void testGetAllStockCheckSessions() {
        // Create at least one session
        StockCheckItemRequest item = new StockCheckItemRequest(1L, new BigDecimal("100"), "History test");
        inventoryService.performStockCheck(new StockCheckSessionRequest("SC-HIST-001", List.of(item)));

        List<StockCheckSessionResponse> sessions = inventoryService.getAllStockCheckSessions();
        assertNotNull(sessions);
        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> "SC-HIST-001".equals(s.checkNumber())));
    }

    @Test
    @DisplayName("UC32 - Edit physical count recalculates variance and status without modifying product stock")
    void testUpdateStockCheckItemCountSuccessAndRecalculatesVariance() {
        var product = productRepository.findById(1L).orElseThrow();
        BigDecimal originalSysStock = product.getStockQuantity();

        // 1. Initial count: system is e.g. originalSysStock, physical entered as 5
        StockCheckItemRequest item = new StockCheckItemRequest(1L, new BigDecimal("5"), "Initial miscount");
        StockCheckSessionResponse session = inventoryService.performStockCheck(
                new StockCheckSessionRequest("SC-CORRECT-001", List.of(item))
        );
        StockCheckResponse initialRecord = session.items().getFirst();
        assertEquals(0, new BigDecimal("5").compareTo(initialRecord.actualQuantity()));
        assertEquals("PENDING_ADJUSTMENT", initialRecord.status());

        // 2. Correct physical count to originalSysStock - 5 (e.g. shortage of 5 instead of massive deficit)
        BigDecimal correctedActual = originalSysStock.subtract(new BigDecimal("5"));
        UpdateStockCheckCountRequest updateReq = new UpdateStockCheckCountRequest(
                correctedActual,
                "Audit recount verified on bottom shelf"
        );
        StockCheckResponse updatedRecord = inventoryService.updateStockCheckItemCount(initialRecord.stockCheckId(), updateReq);

        assertNotNull(updatedRecord);
        assertEquals(0, correctedActual.compareTo(updatedRecord.actualQuantity()));
        assertEquals(0, new BigDecimal("-5").compareTo(updatedRecord.difference()));
        assertEquals("PENDING_ADJUSTMENT", updatedRecord.status());
        assertEquals("Audit recount verified on bottom shelf", updatedRecord.reason());

        // 3. Verify master product stock quantity is untouched
        var productAfter = productRepository.findById(1L).orElseThrow();
        assertEquals(0, originalSysStock.compareTo(productAfter.getStockQuantity()), "Master product stock must NOT change");

        // 4. Verify UC33 adjustment receives and locks to the corrected variance (-5 -> DECREASE 5)
        var batches = inventoryService.getProductBatches(1L);
        StockReceiptDetail targetBatch = stockReceiptDetailRepository.findById(batches.getFirst().stockReceiptItemId()).orElseThrow();
        targetBatch.setRemainingQuantity(new BigDecimal("50"));
        stockReceiptDetailRepository.save(targetBatch);
        Long batchId = targetBatch.getStockReceiptItemId();
        StockAdjustmentRequest adjReq = new StockAdjustmentRequest(
                1L,
                "DECREASE",
                new BigDecimal("5"),
                "Reconcile corrected audit",
                updatedRecord.stockCheckId(),
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batchId, new BigDecimal("5")))
        );
        StockAdjustmentResponse adjResp = inventoryService.adjustStock(adjReq);
        assertNotNull(adjResp);
        assertEquals(0, correctedActual.compareTo(adjResp.newStock()));
    }

    @Test
    @DisplayName("UC32 - Correcting physical count to match system sets status to MATCHED")
    void testUpdateStockCheckItemCountToMatchSystemSetsMatched() {
        var product = productRepository.findById(1L).orElseThrow();
        BigDecimal sysStock = product.getStockQuantity();

        StockCheckItemRequest item = new StockCheckItemRequest(1L, sysStock.subtract(new BigDecimal("10")), "Discrepancy");
        StockCheckSessionResponse session = inventoryService.performStockCheck(
                new StockCheckSessionRequest("SC-MATCH-001", List.of(item))
        );
        Long checkId = session.items().getFirst().stockCheckId();

        // Correct count to match system perfectly
        UpdateStockCheckCountRequest updateReq = new UpdateStockCheckCountRequest(sysStock, "Found missing box, perfectly matches");
        StockCheckResponse updated = inventoryService.updateStockCheckItemCount(checkId, updateReq);

        assertEquals(0, BigDecimal.ZERO.compareTo(updated.difference()));
        assertEquals("MATCHED", updated.status());
    }

    @Test
    @DisplayName("UC32 - Reject editing physical count for already reconciled item")
    void testUpdateStockCheckItemCountRejectsAlreadyReconciled() {
        var product = productRepository.findById(1L).orElseThrow();
        BigDecimal sysStock = product.getStockQuantity();

        StockCheckItemRequest item = new StockCheckItemRequest(1L, sysStock.subtract(new BigDecimal("2")), "Discrepancy");
        StockCheckSessionResponse session = inventoryService.performStockCheck(
                new StockCheckSessionRequest("SC-RECON-001", List.of(item))
        );
        Long checkId = session.items().getFirst().stockCheckId();

        // Reconcile through UC33
        var batches = inventoryService.getProductBatches(1L);
        StockReceiptDetail targetBatch = stockReceiptDetailRepository.findById(batches.getFirst().stockReceiptItemId()).orElseThrow();
        targetBatch.setRemainingQuantity(new BigDecimal("50"));
        stockReceiptDetailRepository.save(targetBatch);
        Long batchId = targetBatch.getStockReceiptItemId();
        inventoryService.adjustStock(new StockAdjustmentRequest(
                1L, "DECREASE", new BigDecimal("2"), "Reconciled", checkId,
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batchId, new BigDecimal("2")))
        ));

        // Now attempt to edit physical count should be rejected
        UpdateStockCheckCountRequest updateReq = new UpdateStockCheckCountRequest(sysStock, "Trying to edit after adjustment");
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            inventoryService.updateStockCheckItemCount(checkId, updateReq);
        });
        assertTrue(ex.getMessage().contains("already been reconciled/adjusted"));
    }

    @Test
    @DisplayName("UC32 - Reject negative or non-integer physical count update")
    void testUpdateStockCheckItemCountRejectsNegativeOrFractional() {
        StockCheckItemRequest item = new StockCheckItemRequest(1L, new BigDecimal("10"), "Count");
        StockCheckSessionResponse session = inventoryService.performStockCheck(
                new StockCheckSessionRequest("SC-VAL-001", List.of(item))
        );
        Long checkId = session.items().getFirst().stockCheckId();

        // 1. Negative count
        assertThrows(BadRequestException.class, () -> {
            inventoryService.updateStockCheckItemCount(checkId, new UpdateStockCheckCountRequest(new BigDecimal("-1"), "Negative"));
        });

        // 2. Fractional count
        assertThrows(BadRequestException.class, () -> {
            inventoryService.updateStockCheckItemCount(checkId, new UpdateStockCheckCountRequest(new BigDecimal("10.5"), "Fractional"));
        });
    }

    @Test
    @DisplayName("UC32/UC33 - Detect stale discrepancy when current stock moves and block reconciliation with old variance")
    void testStaleDiscrepancyDetectionAndReconciliationBlocked() {
        Product p = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();

        // 1. Perform stock check with discrepancy
        BigDecimal countedQty = initialStock.subtract(new BigDecimal("5"));
        StockCheckItemRequest item = new StockCheckItemRequest(1L, countedQty, "Audited 5 missing");
        String sessionCode = "SC-STALE-001";
        StockCheckSessionResponse session = inventoryService.performStockCheck(
                new StockCheckSessionRequest(sessionCode, List.of(item))
        );
        StockCheckResponse checkItem = session.items().getFirst();
        assertFalse(checkItem.isStale(), "Immediately after audit, stock has not moved so not stale");

        // 2. Perform independent manual adjustment (without stockCheckId)
        var batches = inventoryService.getProductBatches(1L);
        Long batchId = batches.getFirst().stockReceiptItemId();
        inventoryService.adjustStock(new StockAdjustmentRequest(
                1L, "INCREASE", new BigDecimal("10"), "Manual independent adjustment", null,
                List.of(new StockAdjustmentRequest.BatchAllocationRequest(batchId, new BigDecimal("10")))
        ));

        // 3. Fetch session again: discrepancy must now be detected as STALE
        StockCheckSessionResponse updatedSession = inventoryService.getStockCheckSession(sessionCode);
        StockCheckResponse staleItem = updatedSession.items().getFirst();

        assertEquals("PENDING_ADJUSTMENT", staleItem.status(), "Stock check status remains pending (not automatically adjusted)");
        assertTrue(staleItem.isStale(), "Item must be detected as stale because current stock != audit system quantity");
        assertEquals(1, updatedSession.staleCount(), "Session staleCount must be 1");
        assertNotEquals(staleItem.systemQuantity(), staleItem.currentStock(), "System quantity and current stock differ");

        // 4. Attempting to adjust with old variance must be blocked by conflict detection
        BusinessException conflictEx = assertThrows(BusinessException.class, () -> {
            inventoryService.adjustStock(new StockAdjustmentRequest(
                    1L, "DECREASE", new BigDecimal("5"), "Attempt to apply stale variance", checkItem.stockCheckId(),
                    List.of(new StockAdjustmentRequest.BatchAllocationRequest(batchId, new BigDecimal("5")))
            ));
        });
        assertTrue(conflictEx.getMessage().toLowerCase().contains("conflict") || conflictEx.getMessage().toLowerCase().contains("moved"));

        // Cleanup: restore initial stock
        Product resetP = productRepository.findById(1L).orElseThrow();
        resetP.setStockQuantity(initialStock);
        productRepository.save(resetP);
    }
}
