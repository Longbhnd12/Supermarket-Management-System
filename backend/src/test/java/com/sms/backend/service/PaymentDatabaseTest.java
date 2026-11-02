package com.sms.backend.service;

import com.sms.backend.dto.payment.CreatePaymentRequest;
import com.sms.backend.dto.payment.CashChangeRequest;
import com.sms.backend.dto.sale.*;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import com.sms.backend.service.payment.VnpayGateway;
import com.sms.backend.dto.sale.SaleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

/** SQL Server verification; fixtures and business writes are rolled back after each test. */
@SpringBootTest(properties = {
        "payment.vnpay.tmn-code=TESTCODE", "payment.vnpay.hash-secret=test-only-secret",
        "payment.vnpay.return-url=http://localhost:5173/payments/vnpay/return",
        "payment.vnpay.ipn-url=https://test.invalid/api/v1/payments/vnpay/ipn",
        "payment.vnpay.enabled-bank-codes=VNPAYQR", "payment.reconcile-delay-ms=600000"
})
@Transactional
class PaymentDatabaseTest {
    @Autowired SaleService sales;
    @Autowired PaymentService payments;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager em;
    @Autowired PaymentRepository paymentRepository;
    @Autowired VnpayGateway gateway;

    @Test void cashPaymentPersistsLocksTheDraftAndReplaysWithoutCreatingAnotherAttempt() {
        var sale = fixtureSale();
        var request = new CreatePaymentRequest(PaymentMethod.CASH, new BigDecimal("100"), new BigDecimal("150"), null, "test-" + UUID.randomUUID());
        var result = payments.create(sale.saleId(), request, "127.0.0.1");
        em.flush(); em.clear();
        assertEquals(PaymentStatus.SUCCESS, result.status()); assertEquals(new BigDecimal("50.00"), result.changeAmount());
        assertEquals(result.paymentId(), payments.create(sale.saleId(), request, "127.0.0.1").paymentId());
        assertEquals(1, payments.overview(sale.saleId()).attempts().size()); assertTrue(payments.overview(sale.saleId()).locked());
        var stored = sales.getSaleById(sale.saleId());
        assertTrue(em.find(Sale.class, sale.saleId()).isCompletionRequested());
        assertEquals(SaleStatus.DRAFT, stored.status()); assertNull(stored.invoiceNumber()); assertNull(stored.completedAt());
        assertThrows(BadRequestException.class, () -> sales.updateItemQuantity(sale.saleId(), stored.items().getFirst().saleItemId(), new UpdateSaleItemRequest(new BigDecimal("2"))));
        assertEquals(0, BigDecimal.TEN.compareTo(products.findById(stored.items().getFirst().productId()).orElseThrow().getStockQuantity()));
    }

    private SaleResponse fixtureSale() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Role role = roles.findByRoleName(RoleName.CASHIER).orElseGet(() -> {
            Role r = new Role(); r.setRoleName(RoleName.CASHIER); return roles.save(r);
        });
        User user = new User(); user.setRole(role); user.setUsername("test" + suffix); user.setEmail(suffix + "@test.invalid");
        user.setPasswordHash("unusable-test-fixture"); users.save(user);
        Category category = new Category(); category.setCategoryName("Test " + suffix); category.setCreatedAt(LocalDateTime.now()); categories.save(category);
        Product product = new Product(); product.setCategory(category); product.setProductCode(suffix); product.setBarcode(suffix);
        product.setProductName("UC28 rollback test"); product.setUnit("piece"); product.setSellingPrice(new BigDecimal("100.00")); product.setStockQuantity(new BigDecimal("10")); products.save(product);
        Supplier supplier = new Supplier(); supplier.setSupplierCode(suffix); supplier.setSupplierName("Payment stock fixture");
        supplier.setPhone("000"); supplier.setCreatedAt(LocalDateTime.now()); supplier.setUpdatedAt(LocalDateTime.now()); em.persist(supplier);
        StockReceipt receipt = new StockReceipt(); receipt.setReceiptNumber(suffix); receipt.setSupplier(supplier); receipt.setCreatedBy(user);
        receipt.setStatus(StockReceiptStatus.CONFIRMED); receipt.setTotalCost(BigDecimal.TEN);
        receipt.setCreatedAt(LocalDateTime.now()); receipt.setReceivedAt(LocalDateTime.now()); em.persist(receipt);
        StockReceiptDetail batch = new StockReceiptDetail(); batch.setStockReceipt(receipt); batch.setProduct(product);
        batch.setQuantity(BigDecimal.TEN); batch.setRemainingQuantity(BigDecimal.TEN); batch.setUnitCost(BigDecimal.ONE);
        batch.setSubtotal(BigDecimal.TEN); em.persist(batch);
        var sale = sales.createSale(new CreateSaleRequest(user.getUserId()));
        return sales.addItemToSale(sale.saleId(), new SaleItemRequest(product.getProductId(), null, BigDecimal.ONE));
    }

    @Test void signedFailureAllowsNewAttemptAndSignedSuccessLocksWithoutCompletingSale() {
        var sale = fixtureSale();
        var request = new CreatePaymentRequest(PaymentMethod.QR, new BigDecimal("100"), null, "VNPAYQR", "test-" + UUID.randomUUID());
        var first = payments.create(sale.saleId(), request, "127.0.0.1");
        assertTrue(payments.overview(sale.saleId()).locked());
        var callback = new HashMap<String, String>();
        callback.put("vnp_TmnCode", "TESTCODE"); callback.put("vnp_TxnRef", first.txnRef()); callback.put("vnp_Amount", "10000");
        callback.put("vnp_ResponseCode", "24"); callback.put("vnp_TransactionStatus", "02");
        callback.put("vnp_SecureHash", gateway.sign(VnpayGateway.canonical(callback)));
        assertEquals("00", payments.ipn(callback).rspCode()); em.flush(); em.clear();
        assertFalse(payments.overview(sale.saleId()).locked());
        var second = payments.create(sale.saleId(), new CreatePaymentRequest(PaymentMethod.QR, new BigDecimal("100"), null,
                "VNPAYQR", "test-" + UUID.randomUUID()), "127.0.0.1");
        assertNotEquals(first.txnRef(), second.txnRef());
        callback.put("vnp_TxnRef", second.txnRef()); callback.put("vnp_ResponseCode", "00"); callback.put("vnp_TransactionStatus", "00");
        callback.put("vnp_TransactionNo", "123456"); callback.put("vnp_PayDate", "20261030100000");
        callback.put("vnp_SecureHash", gateway.sign(VnpayGateway.canonical(callback)));
        assertEquals("00", payments.ipn(callback).rspCode());
        assertEquals("02", payments.ipn(callback).rspCode()); em.flush(); em.clear();
        assertEquals(2, payments.overview(sale.saleId()).attempts().size()); assertTrue(payments.overview(sale.saleId()).locked());
        assertEquals(PaymentStatus.SUCCESS, payments.findByReference(second.txnRef()).status());
        var stored = sales.getSaleById(sale.saleId()); assertEquals(SaleStatus.DRAFT, stored.status()); assertNull(stored.invoiceNumber());
    }

    @Test void repeatedCashPreviewsDoNotPersistPaymentOrChangeSaleAndStock() {
        var sale = fixtureSale();
        em.flush(); em.clear();
        var before = sales.getSaleById(sale.saleId());
        long paymentCount = paymentRepository.count();
        var enough = payments.previewCashChange(sale.saleId(), new CashChangeRequest(new BigDecimal("150"), sale.totalAmount()));
        var shortfall = payments.previewCashChange(sale.saleId(), new CashChangeRequest(BigDecimal.ZERO, sale.totalAmount()));
        assertEquals(new BigDecimal("50.00"), enough.changeAmount()); assertEquals(new BigDecimal("100.00"), shortfall.shortfallAmount());
        em.flush(); em.clear();
        assertEquals(paymentCount, paymentRepository.count()); assertTrue(payments.overview(sale.saleId()).attempts().isEmpty());
        assertFalse(payments.overview(sale.saleId()).locked());
        var unchanged = sales.getSaleById(sale.saleId());
        assertEquals(before, unchanged);
        assertEquals(0, BigDecimal.TEN.compareTo(products.findById(sale.items().getFirst().productId()).orElseThrow().getStockQuantity()));
        var result = payments.create(sale.saleId(), new CreatePaymentRequest(PaymentMethod.CASH, sale.totalAmount(), enough.receivedAmount(), null,
                "preview-confirm-" + UUID.randomUUID()), "127.0.0.1");
        assertEquals(enough.changeAmount(), result.changeAmount()); assertEquals(PaymentStatus.SUCCESS, result.status());
    }
}
