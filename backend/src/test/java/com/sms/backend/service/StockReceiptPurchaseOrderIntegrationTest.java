package com.sms.backend.service;

import com.sms.backend.dto.inventory.CreateStockReceiptItemRequest;
import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class StockReceiptPurchaseOrderIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderDetailRepository purchaseOrderDetailRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private UserRepository userRepository;

    private Supplier supplierA;
    private Supplier supplierB;
    private User testUser;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        supplierA = supplierRepository.findById(1L).orElseThrow();
        supplierB = supplierRepository.findById(2L).orElseThrow();
        testUser = userRepository.findAll().getFirst();
        product1 = productRepository.findById(1L).orElseThrow();
        product2 = productRepository.findById(2L).orElseThrow();
    }

    private PurchaseOrder createTestPo(Supplier supplier, PurchaseOrderStatus status, Product prod, BigDecimal orderedQty) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("PO-TEST-" + System.nanoTime());
        po.setSupplier(supplier);
        po.setRequestedBy(testUser);
        po.setApprovedBy(testUser);
        po.setTotalAmount(orderedQty.multiply(new BigDecimal("10000.00")));
        po.setExpectedDeliveryDate(LocalDate.now().plusDays(7));
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        po = purchaseOrderRepository.save(po);

        PurchaseOrderDetail pod = new PurchaseOrderDetail();
        pod.setPurchaseOrder(po);
        pod.setProduct(prod);
        pod.setRequestedQuantity(orderedQty);
        pod.setOrderedQuantity(orderedQty);
        pod.setReceivedQuantity(BigDecimal.ZERO);
        pod.setUnitPrice(new BigDecimal("10000.00"));
        purchaseOrderDetailRepository.save(pod);

        return po;
    }

    @Test
    @DisplayName("PO selector: only ACCEPTED and PARTIALLY_FULFILLED orders for the supplier are returned")
    void testEligiblePurchaseOrdersFilter() {
        PurchaseOrder acceptedPo = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));
        PurchaseOrder partialPo = createTestPo(supplierA, PurchaseOrderStatus.PARTIALLY_FULFILLED, product1, new BigDecimal("20.00"));
        PurchaseOrder pendingPo = createTestPo(supplierA, PurchaseOrderStatus.PENDING_APPROVAL, product1, new BigDecimal("20.00"));
        PurchaseOrder fulfilledPo = createTestPo(supplierA, PurchaseOrderStatus.FULFILLED, product1, new BigDecimal("20.00"));
        PurchaseOrder otherSupPo = createTestPo(supplierB, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        List<PurchaseOrderResponse> eligible = purchaseOrderService.getEligiblePurchaseOrdersBySupplier(supplierA.getSupplierId());

        List<Long> eligibleIds = eligible.stream().map(PurchaseOrderResponse::orderId).toList();
        assertTrue(eligibleIds.contains(acceptedPo.getOrderId()));
        assertTrue(eligibleIds.contains(partialPo.getOrderId()));
        assertFalse(eligibleIds.contains(pendingPo.getOrderId()));
        assertFalse(eligibleIds.contains(fulfilledPo.getOrderId()));
        assertFalse(eligibleIds.contains(otherSupPo.getOrderId()));
    }

    @Test
    @DisplayName("Reject PO from a different supplier")
    void testRejectPurchaseOrderFromDifferentSupplier() {
        PurchaseOrder poOfSupB = createTestPo(supplierB, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("5.00"),
                new BigDecimal("10000.00"),
                "BATCH-SUP-DIFF",
                LocalDate.now().plusMonths(6)
        );

        CreateStockReceiptRequest req = new CreateStockReceiptRequest(
                null,
                supplierA.getSupplierId(),
                poOfSupB.getOrderId(),
                StockReceiptStatus.CONFIRMED,
                List.of(item)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.createStockReceipt(req)
        );
        assertTrue(ex.getMessage().contains("does not belong to supplier"));
    }

    @Test
    @DisplayName("Direct stock receipt without PO works normally")
    void testDirectStockReceiptWithoutPoWorks() {
        BigDecimal initialStock = product1.getStockQuantity();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("10.00"),
                new BigDecimal("12000.00"),
                "BATCH-DIRECT-01",
                LocalDate.now().plusMonths(6)
        );

        CreateStockReceiptRequest req = new CreateStockReceiptRequest(
                null,
                supplierA.getSupplierId(),
                null,
                StockReceiptStatus.CONFIRMED,
                List.of(item)
        );

        StockReceiptResponse resp = inventoryService.createStockReceipt(req);
        assertNotNull(resp);
        assertNull(resp.purchaseOrderId());

        Product updatedProd = productRepository.findById(product1.getProductId()).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("10.00")).compareTo(updatedProd.getStockQuantity()));
    }

    @Test
    @DisplayName("Save Draft with PO link does NOT update inventory or PO received quantities")
    void testSaveDraftDoesNotAffectInventoryOrPo() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));
        BigDecimal initialStock = product1.getStockQuantity();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("12.00"),
                new BigDecimal("10000.00"),
                "BATCH-DRAFT-PO",
                LocalDate.now().plusMonths(6)
        );

        CreateStockReceiptRequest req = new CreateStockReceiptRequest(
                null,
                supplierA.getSupplierId(),
                po.getOrderId(),
                StockReceiptStatus.DRAFT,
                List.of(item)
        );

        StockReceiptResponse draft = inventoryService.createStockReceipt(req);
        assertEquals(StockReceiptStatus.DRAFT, draft.status());
        assertEquals(po.getOrderId(), draft.purchaseOrderId());

        // Product stock unchanged
        Product prodAfter = productRepository.findById(product1.getProductId()).orElseThrow();
        assertEquals(0, initialStock.compareTo(prodAfter.getStockQuantity()));

        // PO status and received quantity unchanged
        PurchaseOrder poAfter = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.ACCEPTED, poAfter.getStatus());
        PurchaseOrderDetail pod = purchaseOrderDetailRepository.findByPurchaseOrder(po).getFirst();
        assertEquals(0, BigDecimal.ZERO.compareTo(pod.getReceivedQuantity()));
    }

    @Test
    @DisplayName("Edit Draft preserves linked PO and does not treat draft quantities as received")
    void testEditDraftPreservesLinkedPo() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        CreateStockReceiptItemRequest draftItem = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("5.00"),
                new BigDecimal("10000.00"),
                "BATCH-EDIT-DRAFT",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse draft = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.DRAFT, List.of(draftItem)
        ));

        // Update draft with 12 units
        CreateStockReceiptItemRequest updatedItem = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("12.00"),
                new BigDecimal("10000.00"),
                "BATCH-EDIT-DRAFT",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse updated = inventoryService.updateStockReceipt(draft.stockReceiptId(), new CreateStockReceiptRequest(
                draft.receiptNumber(), supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.DRAFT, List.of(updatedItem)
        ));

        assertEquals(po.getOrderId(), updated.purchaseOrderId());
        assertEquals(StockReceiptStatus.DRAFT, updated.status());

        PurchaseOrder poAfter = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.ACCEPTED, poAfter.getStatus());
    }

    @Test
    @DisplayName("Cancel Draft preserves receipt as CANCELLED without modifying inventory or PO")
    void testCancelDraftDoesNotAffectInventoryOrPo() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));
        BigDecimal initialStock = product1.getStockQuantity();

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("8.00"),
                new BigDecimal("10000.00"),
                "BATCH-CANCEL-PO",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse draft = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.DRAFT, List.of(item)
        ));

        StockReceiptResponse cancelled = inventoryService.cancelStockReceipt(draft.stockReceiptId());
        assertEquals(StockReceiptStatus.CANCELLED, cancelled.status());

        Product prodAfter = productRepository.findById(product1.getProductId()).orElseThrow();
        assertEquals(0, initialStock.compareTo(prodAfter.getStockQuantity()));

        PurchaseOrder poAfter = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.ACCEPTED, poAfter.getStatus());
    }

    @Test
    @DisplayName("Partial fulfillment: Confirm 12 of 20 units updates PO to PARTIALLY_FULFILLED, second receipt of 8 updates to FULFILLED")
    void testPartialDeliveryFollowedByFullFulfillment() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));
        BigDecimal initialStock = product1.getStockQuantity();

        // 1. Confirm first stock receipt: 12 units
        CreateStockReceiptItemRequest item1 = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("12.00"),
                new BigDecimal("10000.00"),
                "BATCH-PARTIAL-01",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse receipt1 = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(item1)
        ));

        assertEquals(StockReceiptStatus.CONFIRMED, receipt1.status());

        // Verify PO after 1st receipt
        PurchaseOrder poAfter1 = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.PARTIALLY_FULFILLED, poAfter1.getStatus());
        PurchaseOrderDetail pod1 = purchaseOrderDetailRepository.findByPurchaseOrder(po).getFirst();
        assertEquals(0, new BigDecimal("12.00").compareTo(pod1.getReceivedQuantity()));

        // Verify product inventory
        Product prod1 = productRepository.findById(product1.getProductId()).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("12.00")).compareTo(prod1.getStockQuantity()));

        // 2. Confirm second stock receipt: remaining 8 units
        CreateStockReceiptItemRequest item2 = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("8.00"),
                new BigDecimal("10000.00"),
                "BATCH-PARTIAL-02",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse receipt2 = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(item2)
        ));

        assertEquals(StockReceiptStatus.CONFIRMED, receipt2.status());

        // Verify PO after 2nd receipt: now FULFILLED
        PurchaseOrder poAfter2 = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.FULFILLED, poAfter2.getStatus());
        PurchaseOrderDetail pod2 = purchaseOrderDetailRepository.findByPurchaseOrder(po).getFirst();
        assertEquals(0, new BigDecimal("20.00").compareTo(pod2.getReceivedQuantity()));

        // Verify product inventory total
        Product prod2 = productRepository.findById(product1.getProductId()).orElseThrow();
        assertEquals(0, initialStock.add(new BigDecimal("20.00")).compareTo(prod2.getStockQuantity()));
    }

    @Test
    @DisplayName("Over-receiving: receiving more than remaining quantity is rejected with clear error")
    void testRejectReceivingMoreThanRemainingQuantity() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        // Receive 15 units first
        CreateStockReceiptItemRequest item1 = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("15.00"),
                new BigDecimal("10000.00"),
                "BATCH-OVER-01",
                LocalDate.now().plusMonths(6)
        );
        inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(item1)
        ));

        // Attempt to receive 6 more (remaining is 5)
        CreateStockReceiptItemRequest itemOver = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("6.00"),
                new BigDecimal("10000.00"),
                "BATCH-OVER-02",
                LocalDate.now().plusMonths(6)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                        null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(itemOver)
                ))
        );

        assertTrue(ex.getMessage().contains("exceeds remaining ordered quantity"));
    }

    @Test
    @DisplayName("Product outside the linked PO is rejected")
    void testRejectProductOutsideLinkedPo() {
        // PO only contains product1
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        CreateStockReceiptItemRequest itemWrongProduct = new CreateStockReceiptItemRequest(
                product2.getProductId(),
                new BigDecimal("5.00"),
                new BigDecimal("10000.00"),
                "BATCH-WRONG-PROD",
                LocalDate.now().plusMonths(6)
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                        null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(itemWrongProduct)
                ))
        );

        assertTrue(ex.getMessage().contains("is not included in purchase order"));
    }

    @Test
    @DisplayName("One product split across multiple batches within same receipt is summed accurately")
    void testOneProductSplitAcrossMultipleBatchesSummedCorrectly() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        LocalDate date1 = LocalDate.now().plusMonths(6);
        LocalDate date2 = LocalDate.now().plusMonths(9);

        // Product 1 received in 2 distinct batches: 7 units and 5 units (total 12)
        CreateStockReceiptItemRequest batchA = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("7.00"),
                new BigDecimal("10000.00"),
                "BATCH-MULTI-A",
                date1
        );
        CreateStockReceiptItemRequest batchB = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("5.00"),
                new BigDecimal("10000.00"),
                "BATCH-MULTI-B",
                date2
        );

        StockReceiptResponse resp = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(batchA, batchB)
        ));

        assertEquals(2, resp.details().size());

        // PO received quantity must be 12.00 (7 + 5)
        PurchaseOrderDetail pod = purchaseOrderDetailRepository.findByPurchaseOrder(po).getFirst();
        assertEquals(0, new BigDecimal("12.00").compareTo(pod.getReceivedQuantity()));

        PurchaseOrder poAfter = purchaseOrderRepository.findById(po.getOrderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.PARTIALLY_FULFILLED, poAfter.getStatus());
    }

    @Test
    @DisplayName("Duplicate receipt confirmation cannot double-count quantities")
    void testDuplicateReceiptConfirmationRejected() {
        PurchaseOrder po = createTestPo(supplierA, PurchaseOrderStatus.ACCEPTED, product1, new BigDecimal("20.00"));

        CreateStockReceiptItemRequest item = new CreateStockReceiptItemRequest(
                product1.getProductId(),
                new BigDecimal("10.00"),
                new BigDecimal("10000.00"),
                "BATCH-DUP-01",
                LocalDate.now().plusMonths(6)
        );

        StockReceiptResponse confirmed = inventoryService.createStockReceipt(new CreateStockReceiptRequest(
                null, supplierA.getSupplierId(), po.getOrderId(), StockReceiptStatus.CONFIRMED, List.of(item)
        ));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                inventoryService.confirmStockReceipt(confirmed.stockReceiptId())
        );

        assertTrue(ex.getMessage().contains("already confirmed"));

        // Received quantity must still be exactly 10
        PurchaseOrderDetail pod = purchaseOrderDetailRepository.findByPurchaseOrder(po).getFirst();
        assertEquals(0, new BigDecimal("10.00").compareTo(pod.getReceivedQuantity()));
    }
}

