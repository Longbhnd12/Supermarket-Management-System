package com.sms.backend.service;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderDetailResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
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

        // Expected total: 20 * 15000 + 10 * 25000 = 300000 + 250000 = 550000.00
        LocalDate deliveryDate = LocalDate.now().plusDays(7);
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                null, // auto-generated
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

        // Verify detail lines in response
        PurchaseOrderDetailResponse d1 = response.details().stream()
                .filter(d -> d.productId().equals(1L))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(d1.requestedQuantity()));
        assertNull(d1.orderedQuantity());
        assertEquals(0, new BigDecimal("15000.00").compareTo(d1.unitPrice()));
        assertEquals(0, new BigDecimal("300000.00").compareTo(d1.subtotal()));

        // Verify entity in DB
        PurchaseOrder savedPo = purchaseOrderRepository.findById(response.orderId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, savedPo.getStatus());
        assertNull(savedPo.getApprovedBy());
        assertEquals(0, new BigDecimal("550000.00").compareTo(savedPo.getTotalAmount()));

        // CRITICAL: Verify Product stock was NOT altered
        Product updatedP1 = productRepository.findById(1L).orElseThrow();
        Product updatedP2 = productRepository.findById(6L).orElseThrow();
        assertEquals(0, initialStock1.compareTo(updatedP1.getStockQuantity()));
        assertEquals(0, initialStock2.compareTo(updatedP2.getStockQuantity()));

        // CRITICAL: Verify NO StockMovement records were created
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

        // Duplicate number should fail
        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(request));
    }

    @Test
    @DisplayName("UC46 - Validation: missing or past delivery date throws BadRequestException")
    void testValidationPastDeliveryDate() {
        PurchaseOrderDetailRequest item = new PurchaseOrderDetailRequest(
                1L,
                new BigDecimal("5"),
                null,
                new BigDecimal("10000.00")
        );

        // Past date
        PurchaseOrderRequest pastDateRequest = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now().minusDays(1),
                null,
                List.of(item)
        );
        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(pastDateRequest));

        // Today's date (must be in future)
        PurchaseOrderRequest todayRequest = new PurchaseOrderRequest(
                null,
                1L,
                LocalDate.now(),
                null,
                List.of(item)
        );
        assertThrows(BadRequestException.class, () -> purchaseOrderService.createPurchaseOrder(todayRequest));
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
}
