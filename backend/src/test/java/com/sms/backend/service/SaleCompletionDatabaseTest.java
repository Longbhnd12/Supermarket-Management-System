package com.sms.backend.service;

import com.sms.backend.dto.sale.InvoiceResponse;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Commits only UUID-labelled fixtures to verify separate transactions and SQL Server locks. */
@SpringBootTest(properties = {"sale.completion-delay-ms=600000", "payment.reconcile-delay-ms=600000"})
class SaleCompletionDatabaseTest {
    @Autowired SaleCompletionService completion;
    @Autowired InvoicePdfService pdf;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired Clock clock;
    @Autowired SaleService sales;
    @Autowired PaymentService payments;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.sms.backend.repository.SystemLogRepository logs;
    Long productId, categoryId, supplierId, userId, receiptId;
    Long promotionId;
    List<Long> saleIds = new ArrayList<>();
    List<Long> batchIds = new ArrayList<>();

    void fixture(String stock, String... quantities) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            Role role = em.createQuery("select r from Role r", Role.class).setMaxResults(1).getSingleResult();
            User user = new User(); user.setRole(role); user.setUsername("uc30-" + suffix); user.setEmail(suffix + "@test.invalid");
            user.setPasswordHash("test-only"); em.persist(user); userId = user.getUserId();
            Category category = new Category(); category.setCategoryName("UC30 " + suffix); category.setCreatedAt(LocalDateTime.now(clock));
            em.persist(category); categoryId = category.getCategoryId();
            Product product = new Product(); product.setCategory(category); product.setProductCode(suffix);
            product.setProductName("Sữa tươi tiếng Việt"); product.setUnit("Hộp"); product.setSellingPrice(new BigDecimal("100"));
            product.setStockQuantity(new BigDecimal(stock)); product.setTrackExpiry(true); em.persist(product); productId = product.getProductId();
            Supplier supplier = new Supplier(); supplier.setSupplierCode(suffix); supplier.setSupplierName("UC30 fixture"); supplier.setPhone("000");
            supplier.setCreatedAt(LocalDateTime.now(clock)); supplier.setUpdatedAt(LocalDateTime.now(clock)); em.persist(supplier); supplierId = supplier.getSupplierId();
            StockReceipt receipt = new StockReceipt(); receipt.setReceiptNumber(suffix); receipt.setSupplier(supplier); receipt.setCreatedBy(user);
            receipt.setTotalCost(new BigDecimal("100")); receipt.setStatus(StockReceiptStatus.CONFIRMED);
            receipt.setCreatedAt(LocalDateTime.now(clock)); receipt.setReceivedAt(LocalDateTime.now(clock)); em.persist(receipt); receiptId = receipt.getStockReceiptId();
            // Deliberately insert later expiry first. Expired and undated batches must not be consumed.
            batch(receipt, product, "1", LocalDate.now(clock).plusDays(2));
            batch(receipt, product, "1", LocalDate.now(clock));
            batch(receipt, product, "10", LocalDate.now(clock).minusDays(1));
            batch(receipt, product, "10", null);
            for (String quantity : quantities) {
                BigDecimal qty = new BigDecimal(quantity), amount = qty.multiply(new BigDecimal("100")).setScale(2);
                Sale sale = new Sale(); sale.setSaleNumber("test-" + UUID.randomUUID()); sale.setCashier(user); sale.setCreatedAt(LocalDateTime.now(clock));
                sale.setSubtotal(amount); sale.setDiscountAmount(BigDecimal.ZERO); sale.setTotalAmount(amount); em.persist(sale); saleIds.add(sale.getSaleId());
                SaleItem item = new SaleItem(); item.setSale(sale); item.setProduct(product); item.setQuantity(qty); item.setUnitPrice(new BigDecimal("100"));
                item.setDiscountAmount(BigDecimal.ZERO); item.setSubtotal(amount); em.persist(item);
                Payment payment = new Payment(); payment.setSale(sale); payment.setPaymentMethod(PaymentMethod.CASH); payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setAmount(amount); payment.setReceivedAmount(amount); payment.setChangeAmount(BigDecimal.ZERO); payment.setPaidAt(LocalDateTime.now(clock)); em.persist(payment);
            }
        });
    }
    void batch(StockReceipt receipt, Product product, String quantity, LocalDate expiry) {
        StockReceiptDetail batch = new StockReceiptDetail(); batch.setStockReceipt(receipt); batch.setProduct(product);
        batch.setQuantity(new BigDecimal(quantity)); batch.setRemainingQuantity(new BigDecimal(quantity)); batch.setUnitCost(BigDecimal.ONE);
        batch.setSubtotal(new BigDecimal(quantity)); batch.setExpirationDate(expiry); em.persist(batch); batchIds.add(batch.getStockReceiptItemId());
    }
    @AfterEach void cleanup() {
        for (Long id : saleIds) {
            jdbc.update("delete from dbo.stock_movement where reference_type='SALE_ITEM' and reference_id in (select sale_item_id from dbo.sale_item where sale_id=?)", id);
            jdbc.update("delete from dbo.system_log where entity_type='SALE' and entity_id=?", id);
            jdbc.update("delete from dbo.payment where sale_id=?", id);
            jdbc.update("delete from dbo.sale_item where sale_id=?", id); jdbc.update("delete from dbo.sale where sale_id=?", id);
        }
        if (receiptId != null) { jdbc.update("delete from dbo.stock_receipt_detail where stock_receipt_id=?", receiptId); jdbc.update("delete from dbo.stock_receipt where stock_receipt_id=?", receiptId); }
        if (promotionId != null) jdbc.update("delete from dbo.promotion where promotion_id=?", promotionId);
        if (productId != null) jdbc.update("delete from dbo.product where product_id=?", productId);
        if (supplierId != null) jdbc.update("delete from dbo.supplier where supplier_id=?", supplierId);
        if (categoryId != null) jdbc.update("delete from dbo.category where category_id=?", categoryId);
        if (userId != null) jdbc.update("delete from dbo.[user] where user_id=?", userId);
    }
    BigDecimal remaining(Long id) { return jdbc.queryForObject("select remaining_quantity from dbo.stock_receipt_detail where stock_receipt_item_id=?", BigDecimal.class, id); }

    @Test void discountedPaymentCompletesAndInvoiceKeepsDiscountAfterPromotionChanges() {
        fixture("22", "1.5");
        Long saleId = saleIds.getFirst();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            em.createQuery("delete from Payment p where p.sale.saleId = :id").setParameter("id", saleId).executeUpdate();
            Promotion p = new Promotion(); p.setPromotionName("UC27 snapshot fixture");
            p.setProduct(em.find(Product.class, productId)); p.setCreatedBy(em.find(User.class, userId));
            p.setPromotionType(PromotionType.FIXED_AMOUNT); p.setDiscountValue(new BigDecimal("20"));
            p.setStartAt(LocalDateTime.now(clock).minusDays(1)); p.setEndAt(LocalDateTime.now(clock).plusDays(1));
            em.persist(p); promotionId = p.getPromotionId();
            assertEquals(new BigDecimal("130.00"), sales.calculateSaleTotal(saleId).totalAmount());
            payments.create(saleId, new com.sms.backend.dto.payment.CreatePaymentRequest(PaymentMethod.CASH,
                    new BigDecimal("130"), new BigDecimal("150"), null, "uc27-" + UUID.randomUUID()), "127.0.0.1");
        });
        InvoiceResponse invoice = completion.complete(saleId);
        assertEquals(new BigDecimal("20.00"), invoice.discountAmount());
        assertEquals(new BigDecimal("130.00"), invoice.totalAmount());
        assertEquals(new BigDecimal("20.00"), invoice.items().getFirst().discountAmount());
        jdbc.update("update dbo.promotion set discount_value=99, status='INACTIVE' where promotion_id=?", promotionId);
        assertEquals(invoice, completion.invoice(saleId));
        assertEquals(invoice, completion.complete(saleId));
        assertTrue(pdf.render(invoice).length > 0);
    }

    @Test void fefoFractionalSplitImmutableInvoiceAndIdempotentRetry() throws Exception {
        fixture("22", "1.5");
        InvoiceResponse invoice = completion.complete(saleIds.getFirst());
        assertEquals(0, remaining(batchIds.get(1)).signum()); assertEquals(0, new BigDecimal("0.5").compareTo(remaining(batchIds.getFirst())));
        assertEquals(0, new BigDecimal("10").compareTo(remaining(batchIds.get(2)))); assertEquals(0, new BigDecimal("10").compareTo(remaining(batchIds.get(3))));
        assertEquals(2, jdbc.queryForObject("select count(*) from dbo.stock_movement where product_id=?", Integer.class, productId));
        jdbc.update("update dbo.product set product_name=N'Changed later' where product_id=?", productId);
        assertEquals(invoice, completion.complete(saleIds.getFirst())); assertEquals("Sữa tươi tiếng Việt", completion.invoice(saleIds.getFirst()).items().getFirst().productName());
        byte[] bytes = pdf.render(invoice);
        try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("Sữa tươi tiếng Việt")); assertTrue(text.contains(invoice.invoiceNumber()));
            java.nio.file.Path output = java.nio.file.Path.of("target/uc30-invoice.pdf"); java.nio.file.Files.write(output, bytes);
            var rendered = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 110);
            javax.imageio.ImageIO.write(rendered, "png", java.nio.file.Path.of("target/uc30-invoice.png").toFile());
        }
    }
    @Test void failureRollsBackAllInventoryButKeepsCommittedPayment() {
        fixture("22", "3");
        assertThrows(BadRequestException.class, () -> completion.complete(saleIds.getFirst()));
        assertEquals("DRAFT", jdbc.queryForObject("select status from dbo.sale where sale_id=?", String.class, saleIds.getFirst()));
        assertEquals("SUCCESS", jdbc.queryForObject("select payment_status from dbo.payment where sale_id=?", String.class, saleIds.getFirst()));
        assertNull(jdbc.queryForObject("select invoice_number from dbo.sale where sale_id=?", String.class, saleIds.getFirst()));
        assertEquals(0, jdbc.queryForObject("select count(*) from dbo.stock_movement where product_id=?", Integer.class, productId));
    }
    @Test void concurrentSalesCannotOversellTheSameProduct() throws Exception {
        fixture("1", "1", "1");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = saleIds.stream().map(id -> executor.submit(() -> {
                start.await(); try { completion.complete(id); return true; } catch (BadRequestException expected) { return false; }
            })).toList(); start.countDown();
            int success = 0; for (Future<Boolean> result : results) if (result.get(30, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
            assertEquals(0, jdbc.queryForObject("select stock_quantity from dbo.product where product_id=?", BigDecimal.class, productId).signum());
        }
    }
    @Test void auditFailureRollsBackAlreadyAllocatedBatchesAndMovements() {
        fixture("22", "1.5");
        org.mockito.Mockito.doThrow(new IllegalStateException("forced audit failure"))
            .when(logs).save(org.mockito.ArgumentMatchers.any(SystemLog.class));
        var error = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> completion.complete(saleIds.getFirst()));
        assertEquals("forced audit failure", error.getMostSpecificCause().getMessage());
        assertEquals(0, BigDecimal.ONE.compareTo(remaining(batchIds.get(1))));
        assertEquals(0, new BigDecimal("22").compareTo(jdbc.queryForObject("select stock_quantity from dbo.product where product_id=?", BigDecimal.class, productId)));
        assertEquals(0, jdbc.queryForObject("select count(*) from dbo.stock_movement where product_id=?", Integer.class, productId));
        assertEquals("DRAFT", jdbc.queryForObject("select status from dbo.sale where sale_id=?", String.class, saleIds.getFirst()));
        assertEquals("SUCCESS", jdbc.queryForObject("select payment_status from dbo.payment where sale_id=?", String.class, saleIds.getFirst()));
    }
    @Test void invoiceRestEndpointsReturnTheSameDocumentAndPdf() throws Exception {
        fixture("22", "1");
        SaleService saleService = org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass(completion) != null
            ? applicationContext.getBean(SaleService.class) : null;
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new com.sms.backend.controller.SaleController(saleService), new com.sms.backend.controller.InvoiceController(completion, pdf))
            .setControllerAdvice(new com.sms.backend.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sales/" + saleIds.getFirst() + "/complete"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/sales/" + saleIds.getFirst() + "/invoice"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items[0].productName").value("Sữa tươi tiếng Việt"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/sales/" + saleIds.getFirst() + "/invoice/pdf"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"));
    }
    @Autowired org.springframework.context.ApplicationContext applicationContext;
}
