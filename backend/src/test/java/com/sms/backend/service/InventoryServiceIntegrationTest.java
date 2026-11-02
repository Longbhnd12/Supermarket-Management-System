package com.sms.backend.service;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.InventoryResponse;
import com.sms.backend.dto.inventory.StockHistoryFilter;
import com.sms.backend.dto.inventory.StockMovementPageResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

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
}
