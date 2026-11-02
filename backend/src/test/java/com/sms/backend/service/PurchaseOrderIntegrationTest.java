package com.sms.backend.service;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.ApprovePurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.ApprovedItemRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderFilter;
import com.sms.backend.dto.purchaseorder.PurchaseOrderPageResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderStatusCountsResponse;
import com.sms.backend.dto.purchaseorder.RejectPurchaseOrderRequest;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.SupplierRepository;
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
class PurchaseOrderIntegrationTest {

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
    private StockMovementRepository stockMovementRepository;

    // =========================================================================
    // UC46 — Create Purchase Request Tests
    // =========================================================================

    @Test
    @DisplayName("UC46 - Get next request number returns valid format PRxxxx")
    void testGetNextRequestNumber() {
        String nextNumber = purchaseOrderService.getNextRequestNumber();
        assertNotNull(nextNumber);
        assertTrue(nextNumber.startsWith("PR"));
        assertTrue(nextNumber.length() >= 6);
    }

    @Test
    @DisplayName("UC46 - Low stock suggestions returns products at or below minimum stock level")
    void testGetLowStockSuggestions() {
        List<ProductResponse> suggestions = purchaseOrderService.getLowStockSuggestions();
        assertNotNull(suggestions);
        for (ProductResponse p : suggestions) {
            assertEquals(GeneralStatus.ACTIVE, p.status());
            assertTrue(p.stockQuantity().compareTo(p.minimumStockLevel()) <= 0,
                    "Product " + p.productName() + " has stock " + p.stockQuantity() + " > min " + p.minimumStockLevel());
        }
    }

    @Test
    @DisplayName("UC46 - Create purchase request successfully saves PENDING_APPROVAL and does NOT alter stock")
    void testCreatePurchaseRequestSuccess() {
        Product p1 = productRepository.findById(1L).orElseThrow();
        Product p2 = productRepository.findById(6L).orElseThrow();
        BigDecimal initialStock1 = p1.getStockQuantity();
        BigDecimal initialStock2 = p2.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        PurchaseOrderDetailRequest item1 = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("20"),
                null,
                new BigDecimal("15000.00")
        );
        PurchaseOrderDetailRequest item2 = new PurchaseOrderDetailRequest(
                6L,
                new BigDecimal("10"),
                null,
                new BigDecimal("25000.00")
        );

        LocalDate deliveryDate = LocalDate.now().plusDays(7);
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                1L,
                deliveryDate,
                "Urgent restock for weekend sale",
                List.of(item1, item2)
        );

        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);

        assertNotNull(response);
        assertNotNull(response.orderId());
        assertTrue(response.poNumber().startsWith("PR"));
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, response.status());
        assertNull(response.approvedById());
        assertNull(response.approvedByUsername());
        assertEquals("Urgent restock for weekend sale", response.note());
        assertEquals(deliveryDate, response.expectedDeliveryDate());
        assertEquals(0, new BigDecimal("550000.00").compareTo(response.totalAmount()));
        assertEquals(2, response.details().size());

        PurchaseOrderDetailResponse d1 = response.details().stream()
                .filter(d -> d.productId().equals(1L))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(d1.requestedQuantity()));
        assertNull(d1.orderedQuantity());
        assertEquals(0, new BigDecimal("15000.00").compareTo(d1.unitPrice()));
        assertEquals(0, new BigDecimal("300000.00").compareTo(d1.subtotal()));

        PurchaseOrder savedPo = purchaseOrderRepository.findById(response.orderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, savedPo.getStatus());
        assertNull(savedPo.getApprovedBy());
        assertEquals(0, new BigDecimal("550000.00").compareTo(savedPo.getTotalAmount()));

        Product updatedP1 = productRepository.findById(1L).orElseThrow();
        Product updatedP2 = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock1.compareTo(updatedP1.getStockQuantity()));
        assertEquals(0, initialStock2.compareTo(updatedP2.getStockQuantity()));
        assertEquals(initialMovements, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC46 - Create purchase request with custom poNumber")
    void testCreatePurchaseRequestWithCustomPoNumber() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PR-TEST-CUSTOM-01",
                1L,
                LocalDate.now().plusDays(3),
                null,
                List.of(item)
        );

        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);
        assertEquals("PR-TEST-CUSTOM-01", response.poNumber());
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, response.status());

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: past delivery date throws BadRequestException")
    void testValidationPastDeliveryDate() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderRequest pastDateRequest = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().minusDays(1),
                null,
                List.of(item)
        );
        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(pastDateRequest));
    }

    @Test
    @DisplayName("UC46 - Validation: disabled supplier throws BadRequestException")
    void testValidationDisabledSupplier() {
        Supplier disabledSup = new Supplier();
        disabledSup.setSupplierCode("SUP-DIS-01");
        disabledSup.setSupplierName("Disabled Supplier Test");
        disabledSup.setPhone("0123456789");
        disabledSup.setStatus(SupplierStatus.DISABLED);
        disabledSup.setCreatedAt(java.time.LocalDateTime.now());
        disabledSup.setUpdatedAt(java.time.LocalDateTime.now());
        disabledSup = supplierRepository.save(disabledSup);

        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                disabledSup.getSupplierId(),
                LocalDate.now().plusDays(5),
                null,
                List.of(item)
        );

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: duplicate product in details throws BadRequestException")
    void testValidationDuplicateProduct() {
        PurchaseOrderDetailRequest item1 = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderDetailRequest item2 = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("10"),
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().plusDays(5),
                null,
                List.of(item1, item2)
        );

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: zero quantity throws BadRequestException")
    void testValidationZeroQuantity() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                BigDecimal.ZERO,
                null,
                new BigDecimal("10000.00")
        );
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().plusDays(5),
                null,
                List.of(item)
        );

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: negative unit price throws BadRequestException")
    void testValidationNegativeUnitPrice() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("-100.00")
        );
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().plusDays(5),
                null,
                List.of(item)
        );

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: empty items list throws BadRequestException")
    void testValidationEmptyItems() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().plusDays(5),
                null,
                List.of()
        );

        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    // =========================================================================
    // UC47 — Approve Purchase Request Tests
    // =========================================================================

    @Test
    @DisplayName("UC47 - Get pending purchase requests returns only PENDING_APPROVAL status")
    void testGetPendingPurchaseRequests() {
        // Create one pending request
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("10"), null, new BigDecimal("12000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(4), "Pending test", List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        List<PurchaseOrderResponse> pendingList = purchaseOrderService.getPendingPurchaseRequests();
        assertNotNull(pendingList);
        assertFalse(pendingList.isEmpty());
        for (PurchaseOrderResponse po : pendingList) {
            assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, po.status());
        }
    }

    @Test
    @DisplayName("UC47 - Approve request with adjusted quantities: transitions to SENT, saves orderedQuantity, recalculates total, does NOT modify stock")
    void testApproveRequestWithAdjustedQuantities() {
        Product p = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();
        long initialMovements = stockMovementRepository.count();

        // 1. Create PENDING_APPROVAL request with requestedQuantity = 20, unitPrice = 15000.00
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("20"), null, new BigDecimal("15000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), "Requester note", List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);
        Long orderId = created.orderId();

        // 2. Manager adjusts approved quantity down to 12
        ApprovedItemRequest approvedItem = new ApprovedItemRequest(null, 1L, new BigDecimal("12"));
        ApprovePurchaseOrderRequest approveReq = new ApprovePurchaseOrderRequest("Approved with 12 units based on storage capacity", List.of(approvedItem));

        PurchaseOrderResponse approved = purchaseOrderService.approvePurchaseOrder(orderId, approveReq);

        // 3. Verify status transitioned to SENT
        assertEquals(PurchaseOrderStatus.SENT, approved.status());
        assertNotNull(approved.approvedById());
        assertNotNull(approved.approvedByUsername());

        // 4. Verify total amount recalculated: 12 * 15000 = 180000.00 (was 300000.00)
        assertEquals(0, new BigDecimal("180000.00").compareTo(approved.totalAmount()));

        // 5. Verify detail line: orderedQuantity = 12, requestedQuantity = 20 preserved!
        PurchaseOrderDetailResponse d = approved.details().get(0);
        assertEquals(0, new BigDecimal("20").compareTo(d.requestedQuantity()));
        assertEquals(0, new BigDecimal("12").compareTo(d.orderedQuantity()));
        assertEquals(0, new BigDecimal("180000.00").compareTo(d.subtotal()));

        // 6. Verify entity in DB
        PurchaseOrder savedPo = purchaseOrderRepository.findById(orderId).orElseThrow();
        assertEquals(PurchaseOrderStatus.SENT, savedPo.getStatus());
        assertEquals("Approved with 12 units based on storage capacity", savedPo.getResponseReason());
        List<PurchaseOrderDetail> savedDetails = purchaseOrderDetailRepository.findByPurchaseOrder(savedPo);
        assertEquals(0, new BigDecimal("20").compareTo(savedDetails.get(0).getRequestedQuantity()));
        assertEquals(0, new BigDecimal("12").compareTo(savedDetails.get(0).getOrderedQuantity()));

        // 7. CRITICAL: Product stock and stock movements MUST NOT CHANGE
        Product updatedP = productRepository.findById(1L).orElseThrow();
        assertEquals(0, initialStock.compareTo(updatedP.getStockQuantity()));
        assertEquals(initialMovements, stockMovementRepository.count());
    }

    @Test
    @DisplayName("UC47 - Approve request with default quantities: uses requestedQuantity as orderedQuantity")
    void testApproveRequestWithDefaultQuantities() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(6L, new BigDecimal("15"), null, new BigDecimal("20000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), null, List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        // Approve without passing adjusted items
        PurchaseOrderResponse approved = purchaseOrderService.approvePurchaseOrder(created.orderId(), new ApprovePurchaseOrderRequest(null, null));

        assertEquals(PurchaseOrderStatus.SENT, approved.status());
        assertEquals(0, new BigDecimal("300000.00").compareTo(approved.totalAmount()));
        PurchaseOrderDetailResponse d = approved.details().get(0);
        assertEquals(0, new BigDecimal("15").compareTo(d.requestedQuantity()));
        assertEquals(0, new BigDecimal("15").compareTo(d.orderedQuantity()));
    }

    @Test
    @DisplayName("UC47 - Reject request: transitions to REJECTED_BY_MANAGER and records mandatory reason")
    void testRejectRequest() {
        Product p = productRepository.findById(1L).orElseThrow();
        BigDecimal initialStock = p.getStockQuantity();

        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("10"), null, new BigDecimal("15000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), "Need items", List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        RejectPurchaseOrderRequest rejectReq = new RejectPurchaseOrderRequest("Over budget for this category this month", null);
        PurchaseOrderResponse rejected = purchaseOrderService.rejectPurchaseOrder(created.orderId(), rejectReq);

        assertEquals(PurchaseOrderStatus.REJECTED_BY_MANAGER, rejected.status());
        assertNotNull(rejected.approvedById()); // records manager who made the decision
        assertEquals("Over budget for this category this month", rejected.responseReason());

        PurchaseOrder savedPo = purchaseOrderRepository.findById(created.orderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.REJECTED_BY_MANAGER, savedPo.getStatus());
        assertEquals("Over budget for this category this month", savedPo.getResponseReason());

        // Stock must not change
        Product updatedP = productRepository.findById(1L).orElseThrow();
        assertEquals(0, initialStock.compareTo(updatedP.getStockQuantity()));
    }

    @Test
    @DisplayName("UC47 - Validation: rejecting without reason throws BadRequestException")
    void testRejectMissingReason() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("5"), null, new BigDecimal("10000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), null, List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        assertThrows(BadRequestException.class, () ->
                purchaseOrderService.rejectPurchaseOrder(created.orderId(), new RejectPurchaseOrderRequest("", null)));
        assertThrows(BadRequestException.class, () ->
                purchaseOrderService.rejectPurchaseOrder(created.orderId(), new RejectPurchaseOrderRequest("   ", null)));
        assertThrows(BadRequestException.class, () ->
                purchaseOrderService.rejectPurchaseOrder(created.orderId(), new RejectPurchaseOrderRequest(null, null)));
    }

    @Test
    @DisplayName("UC47 - Validation: approving with non-positive quantity throws BadRequestException")
    void testApproveInvalidQuantity() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("5"), null, new BigDecimal("10000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), null, List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        ApprovedItemRequest zeroQtyItem = new ApprovedItemRequest(null, 1L, BigDecimal.ZERO);
        assertThrows(BadRequestException.class, () ->
                purchaseOrderService.approvePurchaseOrder(created.orderId(), new ApprovePurchaseOrderRequest(null, List.of(zeroQtyItem))));

        ApprovedItemRequest negQtyItem = new ApprovedItemRequest(null, 1L, new BigDecimal("-5"));
        assertThrows(BadRequestException.class, () ->
                purchaseOrderService.approvePurchaseOrder(created.orderId(), new ApprovePurchaseOrderRequest(null, List.of(negQtyItem))));
    }

    @Test
    @DisplayName("UC47 - Concurrency / Status check: repeated approval or rejection of non-PENDING request throws BusinessException")
    void testRepeatedApprovalThrowsBusinessException() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("5"), null, new BigDecimal("10000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), null, List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        // First approval succeeds
        purchaseOrderService.approvePurchaseOrder(created.orderId(), new ApprovePurchaseOrderRequest(null, null));

        // Repeated approval must throw BusinessException
        assertThrows(BusinessException.class, () ->
                purchaseOrderService.approvePurchaseOrder(created.orderId(), new ApprovePurchaseOrderRequest(null, null)));

        // Rejection of already approved order must throw BusinessException
        assertThrows(BusinessException.class, () ->
                purchaseOrderService.rejectPurchaseOrder(created.orderId(), new RejectPurchaseOrderRequest("Too late", null)));
    }

    // =========================================================================
    // UC50 - View Purchase Order Status
    // =========================================================================

    @Test
    @DisplayName("UC50 - Page query filters correctly by keyword, status, supplier, and date range")
    void testUC50GetPurchaseOrdersPageWithFilters() {
        // 1. Create a request for supplier 1
        String uniquePo1 = "PRTEST" + System.currentTimeMillis();
        PurchaseOrderDetailRequest item1 = new PurchaseOrderDetailRequest(1L, new BigDecimal("10"), null, new BigDecimal("15000.00"));
        PurchaseOrderRequest req1 = new PurchaseOrderRequest(uniquePo1, 1L, LocalDate.now().plusDays(3), "UniqueNoteForKeyword", List.of(item1));
        PurchaseOrderResponse po1 = purchaseOrderService.createPurchaseOrder(req1);
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, po1.status());

        // 2. Query page filtering by unique keyword
        PurchaseOrderFilter filterKeyword = new PurchaseOrderFilter(
                uniquePo1, null, null, null, null, 0, 10
        );
        PurchaseOrderPageResponse pageRes = purchaseOrderService.getPurchaseOrdersPage(filterKeyword);
        assertNotNull(pageRes);
        assertTrue(pageRes.content().stream().anyMatch(p -> p.poNumber().equals(uniquePo1)));
        assertTrue(pageRes.statusCounts().all() >= 1);
        assertTrue(pageRes.statusCounts().pendingApproval() >= 1);

        // 3. Query with status filter = PENDING_APPROVAL
        PurchaseOrderFilter filterStatus = new PurchaseOrderFilter(
                uniquePo1, 1L, PurchaseOrderStatus.PENDING_APPROVAL, null, null, 0, 10
        );
        PurchaseOrderPageResponse pageStatusRes = purchaseOrderService.getPurchaseOrdersPage(filterStatus);
        assertEquals(1, pageStatusRes.content().size());
        assertEquals(uniquePo1, pageStatusRes.content().get(0).poNumber());

        // 4. Query with status filter = SENT (should NOT contain po1)
        PurchaseOrderFilter filterSent = new PurchaseOrderFilter(
                uniquePo1, 1L, PurchaseOrderStatus.SENT, null, null, 0, 10
        );
        PurchaseOrderPageResponse pageSentRes = purchaseOrderService.getPurchaseOrdersPage(filterSent);
        assertTrue(pageSentRes.content().isEmpty());

        // 5. Status counts should still reflect the existence of po1 under keyword/supplier
        assertEquals(1, pageSentRes.statusCounts().pendingApproval());
        assertEquals(0, pageSentRes.statusCounts().sent());
    }

    @Test
    @DisplayName("UC50 - getStatusCounts returns all 8 status categories")
    void testUC50GetStatusCounts() {
        PurchaseOrderFilter filter = new PurchaseOrderFilter(null, null, null, null, null, 0, 10);
        PurchaseOrderStatusCountsResponse counts = purchaseOrderService.getStatusCounts(filter);
        assertNotNull(counts);
        assertTrue(counts.all() >= 0);
        assertTrue(counts.pendingApproval() >= 0);
        assertTrue(counts.rejectedByManager() >= 0);
        assertTrue(counts.sent() >= 0);
        assertTrue(counts.accepted() >= 0);
        assertTrue(counts.rejectedBySupplier() >= 0);
        assertTrue(counts.partiallyFulfilled() >= 0);
        assertTrue(counts.fulfilled() >= 0);
    }

    @Test
    @DisplayName("UC50 - getPurchaseOrderById returns details with receivedQuantity and receipts")
    void testUC50GetByIdWithDetailsAndReceipts() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(1L, new BigDecimal("5"), null, new BigDecimal("10000.00"));
        PurchaseOrderRequest req = new PurchaseOrderRequest(null, 1L, LocalDate.now().plusDays(5), "Details test", List.of(item));
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(req);

        PurchaseOrderResponse po = purchaseOrderService.getPurchaseOrderById(created.orderId());
        assertNotNull(po);
        assertEquals(created.orderId(), po.orderId());
        assertNotNull(po.details());
        assertNotNull(po.receipts());
        if (!po.details().isEmpty()) {
            assertNotNull(po.details().get(0).receivedQuantity());
        }
    }
}
