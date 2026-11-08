package com.sms.backend.service;

import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.entity.*;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import com.sms.backend.service.serviceImpl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DirectStockAdjustmentUnitTest {

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

    private InventoryServiceImpl inventoryService;
    private User testUser;

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
                systemSettingRepository
        );

        testUser = new User();
        testUser.setUserId(1L);
        testUser.setUsername("inventory_manager");
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement m = invocation.getArgument(0);
            if (m.getMovementId() == null) {
                m.setMovementId(999L);
            }
            return m;
        });
    }

    private Product createProduct(Long id, String code, boolean trackExpiry, BigDecimal stock) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductCode(code);
        p.setProductName("Test Product " + code);
        p.setUnit("Bottle");
        p.setSellingPrice(new BigDecimal("50000"));
        p.setTrackExpiry(trackExpiry);
        p.setStockQuantity(stock);
        p.setStatus(GeneralStatus.ACTIVE);
        return p;
    }

    private StockReceiptDetail createDetail(Long itemId, Product product, String receiptNum, LocalDateTime receivedAt,
                                            BigDecimal originalQty, BigDecimal remainingQty) {
        StockReceipt receipt = new StockReceipt();
        receipt.setStockReceiptId(itemId * 10);
        receipt.setReceiptNumber(receiptNum);
        receipt.setStatus(StockReceiptStatus.CONFIRMED);
        receipt.setReceivedAt(receivedAt);

        StockReceiptDetail detail = new StockReceiptDetail();
        detail.setStockReceiptItemId(itemId);
        detail.setProduct(product);
        detail.setStockReceipt(receipt);
        detail.setQuantity(originalQty);
        detail.setRemainingQuantity(remainingQty);
        detail.setUnitCost(new BigDecimal("40000"));
        detail.setBatchNumber(null);
        detail.setExpirationDate(null);
        return detail;
    }

    @Test
    @DisplayName("Direct Stock DECREASE: Deducts across multiple receipt details in deterministic FIFO order")
    void testDirectStockDecrease_FIFOAllocation_AcrossMultipleReceipts() {
        Product product = createProduct(7L, "PRD-007", false, new BigDecimal("7"));
        when(productRepository.findByIdWithLock(7L)).thenReturn(Optional.of(product));

        LocalDateTime t1 = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 15, 9, 0);

        StockReceiptDetail detail1 = createDetail(10L, product, "SR0001", t1, new BigDecimal("5"), new BigDecimal("3"));
        StockReceiptDetail detail2 = createDetail(20L, product, "SR0002", t2, new BigDecimal("5"), new BigDecimal("4"));

        when(stockReceiptDetailRepository.lockBatches(7L)).thenReturn(new ArrayList<>(List.of(detail2, detail1)));

        StockAdjustmentRequest req = new StockAdjustmentRequest(
                7L, "DECREASE", new BigDecimal("5"), "Damage write-off", null
        );

        StockAdjustmentResponse res = inventoryService.adjustStock(req);

        assertNotNull(res);
        assertEquals(0, new BigDecimal("2").compareTo(res.newStock()));
        assertEquals(0, new BigDecimal("2").compareTo(product.getStockQuantity()));

        // Earlier receipt detail1 (3 available) should be fully depleted (remaining = 0)
        assertEquals(0, BigDecimal.ZERO.compareTo(detail1.getRemainingQuantity()));
        // Later receipt detail2 (4 available) should have 2 deducted (remaining = 2)
        assertEquals(0, new BigDecimal("2").compareTo(detail2.getRemainingQuantity()));

        // Verify StockMovements
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        List<StockMovement> savedMovements = captor.getAllValues();

        assertEquals(StockMovementType.ADJUSTMENT, savedMovements.get(0).getMovementType());
        assertEquals(0, new BigDecimal("-3").compareTo(savedMovements.get(0).getQuantityChange()));
        assertEquals(10L, savedMovements.get(0).getStockReceiptDetail().getStockReceiptItemId());

        assertEquals(StockMovementType.ADJUSTMENT, savedMovements.get(1).getMovementType());
        assertEquals(0, new BigDecimal("-2").compareTo(savedMovements.get(1).getQuantityChange()));
        assertEquals(20L, savedMovements.get(1).getStockReceiptDetail().getStockReceiptItemId());
    }

    @Test
    @DisplayName("Direct Stock DECREASE: Rejects when requested decrease exceeds available receipt details")
    void testDirectStockDecrease_InsufficientStock_Rejected() {
        Product product = createProduct(7L, "PRD-007", false, new BigDecimal("2"));
        when(productRepository.findByIdWithLock(7L)).thenReturn(Optional.of(product));

        LocalDateTime t1 = LocalDateTime.of(2026, 10, 1, 9, 0);
        // Only 1 unit in confirmed receipt details
        StockReceiptDetail detail1 = createDetail(10L, product, "SR0001", t1, new BigDecimal("1"), new BigDecimal("1"));
        when(stockReceiptDetailRepository.lockBatches(7L)).thenReturn(List.of(detail1));

        StockAdjustmentRequest req = new StockAdjustmentRequest(
                7L, "DECREASE", new BigDecimal("2"), "Damage write-off", null
        );

        var ex = assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(req));
        assertTrue(ex.getMessage().contains("Không đủ tồn kho phiếu nhập"));
        assertEquals(0, new BigDecimal("1").compareTo(detail1.getRemainingQuantity()));
        assertEquals(0, new BigDecimal("2").compareTo(product.getStockQuantity()));
    }

    @Test
    @DisplayName("Direct Stock INCREASE: Bounded reverse-FIFO restores into depleted receipts from newest to oldest")
    void testDirectStockIncrease_BoundedReverseFIFO_Restoration() {
        Product product = createProduct(7L, "PRD-007", false, new BigDecimal("13"));
        when(productRepository.findByIdWithLock(7L)).thenReturn(Optional.of(product));

        LocalDateTime t1 = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 20, 9, 0);

        // detail1 capacity: 10 - 6 = 4
        StockReceiptDetail detail1 = createDetail(10L, product, "SR0001", t1, new BigDecimal("10"), new BigDecimal("6"));
        // detail2 capacity: 10 - 7 = 3 (newer receipt)
        StockReceiptDetail detail2 = createDetail(20L, product, "SR0002", t2, new BigDecimal("10"), new BigDecimal("7"));

        when(stockReceiptDetailRepository.lockBatches(7L)).thenReturn(new ArrayList<>(List.of(detail1, detail2)));

        StockAdjustmentRequest req = new StockAdjustmentRequest(
                7L, "INCREASE", new BigDecimal("5"), "Found undamaged stock on shelf", null
        );

        StockAdjustmentResponse res = inventoryService.adjustStock(req);

        assertNotNull(res);
        assertEquals(0, new BigDecimal("18").compareTo(res.newStock()));
        assertEquals(0, new BigDecimal("18").compareTo(product.getStockQuantity()));

        // Reverse-FIFO: Newer receipt detail2 restored first (capacity 3 fully absorbed -> remaining 10)
        assertEquals(0, new BigDecimal("10").compareTo(detail2.getRemainingQuantity()));
        // Remaining 2 units restored to older receipt detail1 (6 + 2 = 8 <= 10)
        assertEquals(0, new BigDecimal("8").compareTo(detail1.getRemainingQuantity()));

        // Verify StockMovements
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        List<StockMovement> savedMovements = captor.getAllValues();

        assertEquals(StockMovementType.ADJUSTMENT, savedMovements.get(0).getMovementType());
        assertEquals(0, new BigDecimal("3").compareTo(savedMovements.get(0).getQuantityChange()));
        assertEquals(20L, savedMovements.get(0).getStockReceiptDetail().getStockReceiptItemId());

        assertEquals(StockMovementType.ADJUSTMENT, savedMovements.get(1).getMovementType());
        assertEquals(0, new BigDecimal("2").compareTo(savedMovements.get(1).getQuantityChange()));
        assertEquals(10L, savedMovements.get(1).getStockReceiptDetail().getStockReceiptItemId());
    }

    @Test
    @DisplayName("Direct Stock INCREASE: Rejects when requested increase exceeds restoration capacity without partial changes")
    void testDirectStockIncrease_ExceedsRestorationCapacity_Rejected() {
        Product product = createProduct(7L, "PRD-007", false, new BigDecimal("18"));
        when(productRepository.findByIdWithLock(7L)).thenReturn(Optional.of(product));

        LocalDateTime t1 = LocalDateTime.of(2026, 10, 1, 9, 0);
        // Capacity is only 10 - 8 = 2
        StockReceiptDetail detail1 = createDetail(10L, product, "SR0001", t1, new BigDecimal("10"), new BigDecimal("8"));
        when(stockReceiptDetailRepository.lockBatches(7L)).thenReturn(List.of(detail1));

        StockAdjustmentRequest req = new StockAdjustmentRequest(
                7L, "INCREASE", new BigDecimal("3"), "Found extra stock", null
        );

        var ex = assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(req));
        assertTrue(ex.getMessage().contains("Dung lượng khôi phục tối đa từ các phiếu nhập đã xuất là 2"));
        // Detail unchanged
        assertEquals(0, new BigDecimal("8").compareTo(detail1.getRemainingQuantity()));
        // Product stock unchanged
        assertEquals(0, new BigDecimal("18").compareTo(product.getStockQuantity()));
    }

    @Test
    @DisplayName("Expiry-tracked product INCREASE without batch allocation is rejected")
    void testExpiryTrackedProduct_IncreaseWithoutAllocation_ThrowsException() {
        Product product = createProduct(1L, "EXP-001", true, new BigDecimal("10"));
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(product));

        StockReceiptDetail detail1 = createDetail(1L, product, "SR0001", LocalDateTime.now(), new BigDecimal("10"), new BigDecimal("10"));
        when(stockReceiptDetailRepository.lockBatches(1L)).thenReturn(List.of(detail1));

        StockAdjustmentRequest req = new StockAdjustmentRequest(
                1L, "INCREASE", new BigDecimal("2"), "Found stock", null
        );

        var ex = assertThrows(BadRequestException.class, () -> inventoryService.adjustStock(req));
        assertTrue(ex.getMessage().contains("Batch allocation is required for expiry-tracked product"));
    }
}

