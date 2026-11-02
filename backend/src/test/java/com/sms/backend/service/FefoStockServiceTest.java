package com.sms.backend.service;

import com.sms.backend.entity.*;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FefoStockServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    StockReceiptDetailRepository batches = mock(StockReceiptDetailRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    FefoStockService service = new FefoStockService(products, batches, clock);
    Product product;
    @BeforeEach void setup() {
        product = new Product(); product.setProductId(1L); product.setProductCode("TEST");
        product.setTrackExpiry(true); product.setStockQuantity(new BigDecimal("100"));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    }
    StockReceiptDetail batch(String quantity, LocalDate expiry, StockReceiptStatus status) {
        StockReceipt receipt = new StockReceipt(); receipt.setStatus(status);
        StockReceiptDetail batch = new StockReceiptDetail(); batch.setStockReceiptItemId(1L);
        batch.setStockReceipt(receipt); batch.setRemainingQuantity(new BigDecimal(quantity)); batch.setExpirationDate(expiry);
        return batch;
    }
    SaleItem line(String qty) {
        SaleItem line = new SaleItem(); line.setProduct(product); line.setQuantity(new BigDecimal(qty)); return line;
    }
    @Test void excludesExpiredUnconfirmedUndatedAndEmptyLots() {
        LocalDate today = LocalDate.now(clock);
        when(batches.lockBatches(1L)).thenReturn(List.of(
            batch("100", today.minusDays(1), StockReceiptStatus.CONFIRMED),
            batch("100", today.plusDays(1), StockReceiptStatus.DRAFT),
            batch("100", null, StockReceiptStatus.CONFIRMED),
            batch("0", today, StockReceiptStatus.CONFIRMED),
            batch("1", today, StockReceiptStatus.CONFIRMED)));
        var error = assertThrows(BadRequestException.class, () -> service.validateBeforePayment(List.of(line("2"))));
        assertTrue(error.getMessage().contains("TEST")); assertTrue(error.getMessage().contains("1"));
    }
    @Test void combinesDuplicateLinesWithoutConsumingStock() {
        var batch = batch("1.5", LocalDate.now(clock), StockReceiptStatus.CONFIRMED);
        when(batches.lockBatches(1L)).thenReturn(List.of(batch));
        assertThrows(BadRequestException.class, () -> service.validateBeforePayment(List.of(line("1"), line("1"))));
        service.validateBeforePayment(List.of(line("1"), line("0.5")));
        assertEquals(new BigDecimal("1.5"), batch.getRemainingQuantity());
        assertEquals(new BigDecimal("100"), product.getStockQuantity());
    }
    @Test void aggregateStockAlsoLimitsAvailability() {
        product.setStockQuantity(BigDecimal.ZERO);
        when(batches.lockBatches(1L)).thenReturn(List.of(batch("10", LocalDate.now(clock), StockReceiptStatus.CONFIRMED)));
        assertThrows(BadRequestException.class, () -> service.validateBeforePayment(List.of(line("1"))));
    }
    @Test void undatedConfirmedStockAllowedForNonExpiryTrackedProduct() {
        product.setTrackExpiry(false);
        when(batches.lockBatches(1L)).thenReturn(List.of(batch("1", null, StockReceiptStatus.CONFIRMED)));
        assertDoesNotThrow(() -> service.validateBeforePayment(List.of(line("1"))));
    }
    @Test void aggregateStockWithoutBatchesIsRejected() {
        when(batches.lockBatches(1L)).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.validateBeforePayment(List.of(line("1"))));
    }
}