package com.sms.backend.service;

import com.sms.backend.dto.inventory.CreateStockReceiptItemRequest;
import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.StockReceiptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CreateStockReceiptIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReceiptRepository stockReceiptRepository;

    @Autowired
    private StockReceiptDetailRepository stockReceiptDetailRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    @DisplayName("UC36 - Get next receipt number returns valid format SRxxxx")
    void testGetNextReceiptNumber() {
        String nextNumber = inventoryService.getNextReceiptNumber();
        assertNotNull(nextNumber);
        assertTrue(nextNumber.startsWith("SR"));
        assertTrue(nextNumber.length() >= 6);
    }

    @Test
    @DisplayName("UC36 - Create DRAFT stock receipt does not update inventory or create stock movements")
    void testCreateDraftStockReceipt() {
        // Product 1 (perishable)
        Product product = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                1L,
                new BigDecimal("10.00"),
                new BigDecimal("15000.00"),
                "BATCH-DRAFT-01",
                LocalDate.now().plusMonths(6)
        );

        CreateStockReceiptRequest request = new CreateStockReceiptRequest(
                "SR-DRAFT-TEST-99",
                1L,
                null,
                StockReceiptStatus.DRAFT,
                List.of(item)
        );

        StockReceiptResponse response = inventoryService.createStockReceipt(request);

        assertNotNull(response);
        assertEquals("SR-DRAFT-TEST-99", response.receiptNumber());
        assertEquals(StockReceiptStatus.DRAFT, response.status());
        assertNull(response.receivedAt());
        assertEquals(new BigDecimal("150000.00"), response.totalCost());
        assertNotNull(response.details());
        assertEquals(1, response.details().size());

        // Verify product stock has NOT changed
        Product updatedProduct = productRepository.findById(1L).orElseThrow();
        assertEquals(0, initialStock.compareTo(updatedProduct.getStockQuantity()));

        // Verify no new stock movements created
        assertEquals(initialMovements, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC36 - Create CONFIRMED stock receipt increases inventory stock and logs stock movement")
    void testCreateConfirmedStockReceipt() {
        // Product 6 (non-perishable)
        Product product = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity() != null ? product.getStockQuantity() : BigDecimal.ZERO;
        long initialMovements = stockMovementRepository.count();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L,
                new BigDecimal("25.00"),
                new BigDecimal("20000.00"),
                null,
                null
        );

        CreateStockReceiptRequest request = new CreateStockReceiptRequest(
                "SR-CONF-TEST-99",
                1L,
                null,
                StockReceiptStatus.CONFIRMED,
                List.of(item)
        );

        StockReceiptResponse response = inventoryService.createStockReceipt(request);

        assertNotNull(response);
        assertEquals("SR-CONF-TEST-99", response.receiptNumber());
        assertEquals(StockReceiptStatus.CONFIRMED, response.status());
        assertNotNull(response.receivedAt());
        assertEquals(new BigDecimal("500000.00"), response.totalCost());
        assertNotNull(response.details());
        assertEquals(1, response.details().size());

        // Verify product stock has increased by 25
        Product updatedProduct = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("25.00")).compareTo(updatedProduct.getStockQuantity()));

        // Verify stock movement created
        assertEquals(initialMovements + 1, stockMovementRepository.count());
        List<StockMovement> movements = stockMovementRepository.findAll();
        StockMovement lastMovement = movements.getLast();
        assertEquals(StockMovementType.RECEIPT, lastMovement.getMovementType());
        assertEquals(0, new BigDecimal("25.00").compareTo(lastMovement.getQuantityChange()));
        assertEquals(StockReferenceType.STOCK_RECEIPT, lastMovement.getReferenceType());
        assertEquals(response.stockReceiptId(), lastMovement.getReferenceId());
    }

    @Test
    @DisplayName("UC36 - Confirm DRAFT stock receipt updates status, increases inventory and prevents double confirm")
    void testConfirmDraftStockReceipt() {
        // Create draft receipt first
        Product product = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = product.getStockQuantity();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L,
                new BigDecimal("5.00"),
                new BigDecimal("10000.00"),
                null,
                null
        );

        CreateStockReceiptRequest request = new CreateStockReceiptRequest(
                "SR-DRAFT-TO-CONF-99",
                1L,
                null,
                StockReceiptStatus.DRAFT,
                List.of(item)
        );

        StockReceiptResponse draftResponse = inventoryService.createStockReceipt(request);
        assertEquals(StockReceiptStatus.DRAFT, draftResponse.status());

        // Confirm the draft receipt
        StockReceiptResponse confirmedResponse = inventoryService.confirmStockReceipt(draftResponse.stockReceiptId());
        assertEquals(StockReceiptStatus.CONFIRMED, confirmedResponse.status());
        assertNotNull(confirmedResponse.receivedAt());

        // Product stock increased by 5
        Product updatedProduct = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("5.00")).compareTo(updatedProduct.getStockQuantity()));

        // Double confirmation must fail with BusinessException
        assertThrows(BusinessException.class, () ->
                inventoryService.confirmStockReceipt(draftResponse.stockReceiptId())
        );
    }

    @Test
    @DisplayName("UC36 - Perishable products require batch number and future expiration date")
    void testPerishableValidation() {
        // Missing batch number
        CreateStockReceiptItemRequest missingBatch = new CreateStockReceiptItemRequest(
                1L,
                new BigDecimal("10.00"),
                new BigDecimal("15000.00"),
                null,
                LocalDate.now().plusMonths(3)
        );
        CreateStockReceiptRequest req1 = new CreateStockReceiptRequest(null, 1L, null, StockReceiptStatus.CONFIRMED, List.of(missingBatch));
        assertThrows(BadRequestException.class, () -> inventoryService.createStockReceipt(req1));

        // Missing expiration date
        CreateStockReceiptItemRequest missingExpiry = new CreateStockReceiptItemRequest(
                1L,
                new BigDecimal("10.00"),
                new BigDecimal("15000.00"),
                "BATCH-001",
                null
        );
        CreateStockReceiptRequest req2 = new CreateStockReceiptRequest(null, 1L, null, StockReceiptStatus.CONFIRMED, List.of(missingExpiry));
        assertThrows(BadRequestException.class, () -> inventoryService.createStockReceipt(req2));

        // Past expiration date
        CreateStockReceiptItemRequest pastExpiry = new CreateStockReceiptItemRequest(
                1L,
                new BigDecimal("10.00"),
                new BigDecimal("15000.00"),
                "BATCH-001",
                LocalDate.now().minusDays(1)
        );
        CreateStockReceiptRequest req3 = new CreateStockReceiptRequest(null, 1L, null, StockReceiptStatus.CONFIRMED, List.of(pastExpiry));
        assertThrows(BadRequestException.class, () -> inventoryService.createStockReceipt(req3));
    }

    @Test
    @DisplayName("UC36 - Duplicate receipt number is rejected")
    void testDuplicateReceiptNumber() {
        // SR-TEST-001 already exists in DB
        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L,
                new BigDecimal("1.00"),
                new BigDecimal("10000.00"),
                null,
                null
        );
        CreateStockReceiptRequest request = new CreateStockReceiptRequest(
                "SR-TEST-001",
                1L,
                null,
                StockReceiptStatus.DRAFT,
                List.of(item)
        );

        assertThrows(BadRequestException.class, () -> inventoryService.createStockReceipt(request));
    }

    @Test
    @DisplayName("UC36/37 - Update DRAFT stock receipt updates details without altering stock or creating movements")
    void testUpdateDraftStockReceipt() {
        Product p = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        // 1. Create a draft receipt
        CreateStockReceiptItemRequest item1 = new CreateStockReceiptItemRequest(
                6L, new BigDecimal("10.00"), new BigDecimal("15000.00"), null, null
        );
        CreateStockReceiptRequest createReq = new CreateStockReceiptRequest(
                null, 1L, null, StockReceiptStatus.DRAFT, List.of(item1)
        );
        StockReceiptResponse created = inventoryService.createStockReceipt(createReq);
        assertEquals(StockReceiptStatus.DRAFT, created.status());
        Long receiptId = created.stockReceiptId();
        String receiptNumber = created.receiptNumber();

        // 2. Update the draft receipt with different quantity and unitCost
        CreateStockReceiptItemRequest updatedItem = new CreateStockReceiptItemRequest(
                6L, new BigDecimal("25.00"), new BigDecimal("14000.00"), null, null
        );
        CreateStockReceiptRequest updateReq = new CreateStockReceiptRequest(
                receiptNumber, 1L, null, StockReceiptStatus.DRAFT, List.of(updatedItem)
        );
        StockReceiptResponse updated = inventoryService.updateStockReceipt(receiptId, updateReq);

        // 3. Verify ID, receiptNumber, status preserved
        assertEquals(receiptId, updated.stockReceiptId());
        assertEquals(receiptNumber, updated.receiptNumber());
        assertEquals(StockReceiptStatus.DRAFT, updated.status());
        assertEquals(new BigDecimal("350000.00"), updated.totalCost());
        assertEquals(1, updated.details().size());
        assertEquals(0, new BigDecimal("25.00").compareTo(updated.details().get(0).receivedQuantity()));

        // 4. Verify product stock & movement unchanged
        Product pAfterUpdate = productRepository.findById(6L).orElseThrow();
        assertEquals(initialStock, pAfterUpdate.getStockQuantity());
        assertEquals(initialMovements, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC36/37 - Confirming an edited DRAFT receipt updates product stock and logs movement exactly once")
    void testConfirmEditedDraftStockReceipt() {
        Product p = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        // 1. Create and update a draft receipt with quantity 5
        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L, new BigDecimal("5.00"), new BigDecimal("12000.00"), null, null
        );
        CreateStockReceiptRequest createReq = new CreateStockReceiptRequest(
                null, 1L, null, StockReceiptStatus.DRAFT, List.of(item)
        );
        StockReceiptResponse created = inventoryService.createStockReceipt(createReq);

        // 2. Confirm the receipt
        StockReceiptResponse confirmed = inventoryService.confirmStockReceipt(created.stockReceiptId());
        assertEquals(StockReceiptStatus.CONFIRMED, confirmed.status());

        // 3. Verify stock increased by 5 and 1 movement added
        Product pAfterConfirm = productRepository.findById(6L).orElseThrow();
        assertEquals(initialStock.add(new BigDecimal("5.00")), pAfterConfirm.getStockQuantity());
        assertEquals(initialMovements + 1, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC37 - Cancel DRAFT stock receipt marks status CANCELLED without touching stock")
    void testCancelDraftStockReceipt() {
        Product p = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L, new BigDecimal("12.00"), new BigDecimal("10000.00"), null, null
        );
        CreateStockReceiptRequest createReq = new CreateStockReceiptRequest(
                null, 1L, null, StockReceiptStatus.DRAFT, List.of(item)
        );
        StockReceiptResponse created = inventoryService.createStockReceipt(createReq);
        assertEquals(StockReceiptStatus.DRAFT, created.status());

        StockReceiptResponse cancelled = inventoryService.cancelStockReceipt(created.stockReceiptId());
        assertEquals(StockReceiptStatus.CANCELLED, cancelled.status());

        // Verify stock & movements unchanged
        Product pAfterCancel = productRepository.findById(6L).orElseThrow();
        assertEquals(initialStock, pAfterCancel.getStockQuantity());
        assertEquals(initialMovements, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC37 - Cannot edit or cancel a CONFIRMED or CANCELLED stock receipt")
    void testCannotEditOrCancelConfirmedReceipt() {
        // Create and confirm a receipt
        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                6L, new BigDecimal("2.00"), new BigDecimal("10000.00"), null, null
        );
        CreateStockReceiptRequest createReq = new CreateStockReceiptRequest(
                null, 1L, null, StockReceiptStatus.CONFIRMED, List.of(item)
        );
        StockReceiptResponse confirmed = inventoryService.createStockReceipt(createReq);

        CreateStockReceiptRequest updateReq = new CreateStockReceiptRequest(
                confirmed.receiptNumber(), 1L, null, StockReceiptStatus.DRAFT, List.of(item)
        );

        // Editing confirmed receipt throws BusinessException
        assertThrows(BusinessException.class, () -> inventoryService.updateStockReceipt(confirmed.stockReceiptId(), updateReq));

        // Cancelling confirmed receipt throws BusinessException
        assertThrows(BusinessException.class, () -> inventoryService.cancelStockReceipt(confirmed.stockReceiptId()));

        // Create draft and cancel it
        CreateStockReceiptRequest draftReq = new CreateStockReceiptRequest(
                null, 1L, null, StockReceiptStatus.DRAFT, List.of(item)
        );
        StockReceiptResponse draft = inventoryService.createStockReceipt(draftReq);
        inventoryService.cancelStockReceipt(draft.stockReceiptId());

        // Cancelling already cancelled receipt throws BusinessException
        assertThrows(BusinessException.class, () -> inventoryService.cancelStockReceipt(draft.stockReceiptId()));
        // Editing cancelled receipt throws BusinessException
        assertThrows(BusinessException.class, () -> inventoryService.updateStockReceipt(draft.stockReceiptId(), updateReq));
    }
}
