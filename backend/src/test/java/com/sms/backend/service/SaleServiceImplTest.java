package com.sms.backend.service;

import com.sms.backend.dto.sale.*;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import com.sms.backend.service.serviceImpl.SaleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {
    @Mock SaleRepository sales;
    @Mock SaleItemRepository items;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock PaymentRepository payments;
    @Mock PromotionRepository promotions;
    SaleServiceImpl service;

    Clock fixedClock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach void setup() {
        service = new SaleServiceImpl(sales, items, products, users, payments,
                mock(SaleCompletionService.class), new SalePromotionCalculator(promotions,
                fixedClock), fixedClock);
    }

    @Test void createsEmptyDraftWithoutInvoice() {
        User user = new User(); user.setUserId(1L); user.setUsername("operator");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(sales.save(any())).thenAnswer(i -> { Sale s = i.getArgument(0); s.setSaleId(7L); return s; });
        SaleResponse result = service.createSale(new CreateSaleRequest(1L));
        assertEquals(SaleStatus.DRAFT, result.status());
        assertEquals(0, result.totalAmount().signum());
        assertNull(result.invoiceNumber()); assertNull(result.completedAt());
        assertNotNull(result.createdAt()); assertTrue(result.saleNumber().length() <= 50);
        assertTrue(result.items().isEmpty());
    }

    @Test void rejectsInactiveCashier() {
        User user = new User(); user.setStatus(AccountStatus.INACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> service.createSale(new CreateSaleRequest(1L)));
        verify(sales, never()).save(any());
    }

    @Test void addsFractionalQuantityAtDatabasePriceAndMapsItems() {
        Sale sale = new Sale(); User user = new User(); user.setUserId(1L);
        sale.setCashier(user); sale.setSubtotal(new BigDecimal("10.00"));
        Product product = new Product(); product.setStockQuantity(new BigDecimal("999999999.999")); product.setProductId(2L); product.setSellingPrice(new BigDecimal("12.35"));
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        when(products.findById(2L)).thenReturn(Optional.of(product));
        when(sales.save(any())).thenAnswer(i -> i.getArgument(0));
        List<SaleItem> lines = new ArrayList<>();
        SaleItem existing = line(1L, "10.00", "1"); existing.getProduct().setProductId(3L);
        lines.add(existing);
        when(items.findBySale(sale)).thenReturn(lines);
        doAnswer(i -> {
            List<SaleItem> saved = i.getArgument(0);
            saved.stream().filter(line -> !lines.contains(line)).forEach(lines::add);
            return saved;
        }).when(items).saveAll(any());
        SaleResponse result = service.addItemToSale(7L, new SaleItemRequest(2L, null, new BigDecimal("0.125")));
        assertEquals(new BigDecimal("11.54"), result.totalAmount());
        assertEquals(new BigDecimal("12.35"), result.items().get(1).unitPrice());
        verify(products, never()).save(any());
    }

    @Test void repeatedAddsIncreaseSameLineAndKeepSnapshotPrice() {
        SaleItem existing = line(11L, "12.35", "1"); existing.getProduct().setProductId(2L);
        Sale sale = draftWithItems(new ArrayList<>(List.of(existing)));
        Product product = new Product(); product.setStockQuantity(new BigDecimal("999999999.999")); product.setProductId(2L); product.setSellingPrice(new BigDecimal("20.00"));
        when(products.findById(2L)).thenReturn(Optional.of(product));
        when(sales.save(sale)).thenReturn(sale);
        var request = new SaleItemRequest(2L, null, BigDecimal.ONE);
        var secondScan = service.addItemToSale(7L, request);
        assertEquals(1, secondScan.items().size());
        assertEquals(11L, secondScan.items().getFirst().saleItemId());
        assertEquals(new BigDecimal("2"), secondScan.items().getFirst().quantity());
        assertEquals(new BigDecimal("24.70"), secondScan.totalAmount());
        var thirdScan = service.addItemToSale(7L, request);
        assertEquals(1, thirdScan.items().size());
        assertEquals(new BigDecimal("3"), thirdScan.items().getFirst().quantity());
        assertEquals(new BigDecimal("37.05"), thirdScan.totalAmount());
        verify(items, times(2)).saveAll(List.of(existing));
    }

    @Test void repeatedAddRejectsCombinedQuantityOverflow() {
        SaleItem existing = line(11L, "1.00", "1"); existing.setQuantity(new BigDecimal("999999999.999")); existing.getProduct().setProductId(2L);
        draftWithItems(List.of(existing));
        Product product = new Product(); product.setStockQuantity(new BigDecimal("999999999.999")); product.setProductId(2L); product.setSellingPrice(BigDecimal.ONE);
        when(products.findById(2L)).thenReturn(Optional.of(product));
        assertThrows(BadRequestException.class, () -> service.addItemToSale(7L, new SaleItemRequest(2L, null, BigDecimal.ONE)));
        verify(items, never()).save(any());
        assertEquals(new BigDecimal("999999999.999"), existing.getQuantity());
    }
    @Test void rejectsCompletedSaleBeforeWriting() {
        Sale sale = new Sale(); sale.setStatus(SaleStatus.COMPLETED);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        assertThrows(BadRequestException.class, () -> service.addItemToSale(7L,
                new SaleItemRequest(2L, null, BigDecimal.ONE)));
        verifyNoInteractions(items, products);
    }

    @Test void preservesLeadingZeroBarcodeAndFiltersInactiveProduct() {
        Product product = new Product(); product.setBarcode("00123"); product.setStatus(GeneralStatus.INACTIVE);
        when(products.findByBarcode("00123")).thenReturn(Optional.of(product));
        assertTrue(service.searchProducts("00123", null).isEmpty());
        verify(products).findByBarcode("00123");
        assertThrows(BadRequestException.class, () -> service.searchProducts("00123", "milk"));
    }

    private Sale draftWithItems(List<SaleItem> lines) {
        Sale sale = new Sale(); sale.setCashier(new User());
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        when(items.findBySale(sale)).thenReturn(lines);
        return sale;
    }

    private SaleItem line(long id, String price, String quantity) {
        SaleItem item = new SaleItem(); item.setSaleItemId(id); item.setProduct(new Product()); item.getProduct().setProductId(id); item.getProduct().setStockQuantity(new BigDecimal("999999999.999"));
        item.setUnitPrice(new BigDecimal(price)); item.setQuantity(new BigDecimal(quantity));
        item.setSubtotal(item.getUnitPrice().multiply(item.getQuantity()).setScale(2));
        return item;
    }

    @Test void quantityReplacementKeepsSnapshotPriceAndRecalculatesAllLines() {
        SaleItem first = line(1L, "12.35", "2");
        SaleItem second = line(2L, "10.00", "1");
        Sale sale = draftWithItems(List.of(first, second));
        when(sales.save(sale)).thenReturn(sale);
        SaleResponse result = service.updateItemQuantity(7L, 1L, new UpdateSaleItemRequest(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("11.54"), result.totalAmount());
        assertEquals(new BigDecimal("1.54"), first.getSubtotal());
        assertEquals(new BigDecimal("12.35"), first.getUnitPrice());
        verifyNoInteractions(products);
    }

    @Test void removingLastItemResetsAllTotals() {
        SaleItem first = line(1L, "12.35", "2");
        List<SaleItem> lines = new ArrayList<>(List.of(first));
        Sale sale = draftWithItems(lines);
        when(sales.save(sale)).thenReturn(sale);
        doAnswer(i -> { lines.remove(first); return null; }).when(items).delete(first);
        SaleResponse result = service.removeItemFromSale(7L, 1L);
        assertEquals(new BigDecimal("0.00"), result.subtotal());
        assertEquals(new BigDecimal("0.00"), result.discountAmount());
        assertEquals(new BigDecimal("0.00"), result.totalAmount());
        assertTrue(result.items().isEmpty());
    }

    @Test void itemFromAnotherSaleCannotBeUpdatedOrRemoved() {
        draftWithItems(List.of(line(1L, "10.00", "1")));
        assertThrows(com.sms.backend.exception.ResourceNotFoundException.class,
                () -> service.updateItemQuantity(7L, 99L, new UpdateSaleItemRequest(BigDecimal.ONE)));
        assertThrows(com.sms.backend.exception.ResourceNotFoundException.class,
                () -> service.removeItemFromSale(7L, 99L));
        verify(items, never()).save(any()); verify(items, never()).delete(any());
    }

    @Test void invalidQuantitiesDoNotWriteItems() {
        Sale sale = new Sale();
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        for (BigDecimal quantity : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("1.0001"), new BigDecimal("1000000000"))) {
            assertThrows(BadRequestException.class,
                    () -> service.updateItemQuantity(7L, 1L, new UpdateSaleItemRequest(quantity)));
        }
        verifyNoInteractions(items);
    }

    @Test void cancelledSaleRejectsQuantityChangesAndRemoval() {
        Sale sale = new Sale(); sale.setStatus(SaleStatus.CANCELLED);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        assertThrows(BadRequestException.class,
                () -> service.updateItemQuantity(7L, 1L, new UpdateSaleItemRequest(BigDecimal.ONE)));
        assertThrows(BadRequestException.class, () -> service.removeItemFromSale(7L, 1L));
        verifyNoInteractions(items);
    }

    @Test void calculationRoundsEachLineAndRepairsStaleSubtotalsIdempotently() {
        SaleItem first = line(1L, "1.00", "1"); first.setQuantity(new BigDecimal("0.005"));
        SaleItem second = line(2L, "1.00", "1"); second.setQuantity(new BigDecimal("0.005"));
        Sale sale = draftWithItems(List.of(first, second));
        when(sales.save(sale)).thenReturn(sale);
        SaleResponse result = service.calculateSaleTotal(7L);
        assertEquals(new BigDecimal("0.01"), result.items().getFirst().subtotal());
        assertEquals(new BigDecimal("0.02"), result.subtotal());
        assertEquals(new BigDecimal("0.02"), result.totalAmount());
        assertEquals(result, service.calculateSaleTotal(7L));
        verifyNoInteractions(products);
    }

    @Test void calculationOfEmptySaleReturnsZeroTotals() {
        Sale sale = draftWithItems(List.of());
        when(sales.save(sale)).thenReturn(sale);
        SaleResponse result = service.calculateSaleTotal(7L);
        assertEquals(new BigDecimal("0.00"), result.totalAmount());
        assertEquals(new BigDecimal("0.00"), result.subtotal());
    }

    @Test void calculationClearsStaleDiscountsWhenNoPromotionIsEligible() {
        SaleItem item = line(1L, "10.00", "2"); item.setDiscountAmount(new BigDecimal("3.00"));
        Sale sale = draftWithItems(List.of(item));
        when(sales.save(sale)).thenReturn(sale);
        SaleResponse result = service.calculateSaleTotal(7L);
        assertEquals(new BigDecimal("20.00"), result.subtotal());
        assertEquals(new BigDecimal("0.00"), result.discountAmount());
        assertEquals(new BigDecimal("20.00"), result.totalAmount());
    }

    @Test void calculationRejectsOverflowBeforeSaving() {
        SaleItem first = line(1L, "9999999999999.99", "1");
        SaleItem second = line(2L, "1.00", "1");
        draftWithItems(List.of(first, second));
        assertThrows(BadRequestException.class, () -> service.calculateSaleTotal(7L));
        verify(sales, never()).save(any()); verify(items, never()).saveAll(any());
    }

    @Test void completedSaleCannotBeRecalculated() {
        Sale sale = new Sale(); sale.setStatus(SaleStatus.COMPLETED);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        assertThrows(BadRequestException.class, () -> service.calculateSaleTotal(7L));
        verifyNoInteractions(items);
    }

    @Test void rejectsInitialAddAboveStockWithoutSaving() {
        draftWithItems(List.of());
        Product p = new Product(); p.setProductId(2L); p.setProductCode("OIL");
        p.setSellingPrice(BigDecimal.TEN); p.setStockQuantity(new BigDecimal("2"));
        when(products.findById(2L)).thenReturn(Optional.of(p));
        var error = assertThrows(BadRequestException.class, () -> service.addItemToSale(7L,
                new SaleItemRequest(2L, null, new BigDecimal("3"))));
        assertTrue(error.getMessage().contains("tồn kho 2"));
        verify(items, never()).save(any()); verify(sales, never()).save(any());
    }

    @Test void repeatedScanCannotExceedAggregateStock() {
        SaleItem a = line(1L, "10.00", "1"); a.getProduct().setProductId(2L);
        SaleItem b = line(2L, "10.00", "1");
        draftWithItems(List.of(a, b));
        Product p = b.getProduct(); p.setSellingPrice(BigDecimal.TEN); p.setStockQuantity(new BigDecimal("2"));
        when(products.findById(2L)).thenReturn(Optional.of(p));
        assertThrows(BadRequestException.class, () -> service.addItemToSale(7L,
                new SaleItemRequest(2L, null, BigDecimal.ONE)));
        assertEquals(BigDecimal.ONE, a.getQuantity());
        verify(items, never()).save(any());
    }

    @Test void replacementAboveStockPreservesSavedQuantityAndSubtotal() {
        SaleItem a = line(1L, "48000.00", "2"); a.getProduct().setStockQuantity(new BigDecimal("2"));
        draftWithItems(List.of(a));
        assertThrows(BadRequestException.class, () -> service.updateItemQuantity(7L, 1L,
                new UpdateSaleItemRequest(new BigDecimal("7"))));
        assertEquals(new BigDecimal("2"), a.getQuantity());
        assertEquals(new BigDecimal("96000.00"), a.getSubtotal());
        verify(items, never()).save(any()); verify(sales, never()).save(any());
    }
    @Test void blockingPaymentRejectsEveryCartMutation() {
        Sale sale = new Sale();
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        when(payments.hasBlockingPayment(sale)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.addItemToSale(7L, new SaleItemRequest(2L, null, BigDecimal.ONE)));
        assertThrows(BadRequestException.class, () -> service.updateItemQuantity(7L, 1L, new UpdateSaleItemRequest(BigDecimal.ONE)));
        assertThrows(BadRequestException.class, () -> service.removeItemFromSale(7L, 1L));
        assertThrows(BadRequestException.class, () -> service.calculateSaleTotal(7L));
        verifyNoInteractions(items, products);
    }

    private Promotion fixedPromotion(Product product, String value) {
        Promotion p = new Promotion(); p.setPromotionId(1L); p.setProduct(product);
        p.setPromotionType(PromotionType.FIXED_AMOUNT); p.setDiscountValue(new BigDecimal(value));
        p.setStartAt(LocalDateTime.of(2026, 11, 1, 0, 0)); p.setEndAt(LocalDateTime.of(2026, 11, 30, 0, 0));
        return p;
    }

    @Test void discountedLineCanBeScannedAgainAndFixedDiscountAppliesOnlyOnce() {
        SaleItem line = line(11L, "100.00", "1"); line.getProduct().setProductId(2L);
        line.getProduct().setSellingPrice(new BigDecimal("999.00"));
        line.setPromotion(fixedPromotion(line.getProduct(), "20")); line.setDiscountAmount(new BigDecimal("20.00"));
        Sale sale = draftWithItems(List.of(line)); when(sales.save(sale)).thenReturn(sale);
        when(products.findById(2L)).thenReturn(Optional.of(line.getProduct()));
        when(promotions.findEffective(eq(GeneralStatus.ACTIVE), any())).thenReturn(List.of(line.getPromotion()));
        var result = service.addItemToSale(7L, new SaleItemRequest(2L, null, BigDecimal.ONE));
        assertEquals(11L, result.items().getFirst().saleItemId());
        assertEquals(new BigDecimal("100.00"), result.items().getFirst().unitPrice());
        assertEquals(new BigDecimal("20.00"), result.discountAmount());
        assertEquals(new BigDecimal("180.00"), result.totalAmount());
        verify(items).saveAll(List.of(line));
    }

    @Test void quantityChangeRefreshesDiscountOnEveryLineAndCalculateIsIdempotent() {
        SaleItem a = line(1L, "100.00", "2"), b = line(2L, "100.00", "1");
        a.setDiscountAmount(new BigDecimal("999.00")); b.setDiscountAmount(new BigDecimal("50.00"));
        Sale sale = draftWithItems(List.of(a, b)); when(sales.save(sale)).thenReturn(sale);
        when(promotions.findEffective(eq(GeneralStatus.ACTIVE), any())).thenReturn(List.of(fixedPromotion(a.getProduct(), "20")));
        var result = service.updateItemQuantity(7L, 1L, new UpdateSaleItemRequest(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("12.50"), a.getDiscountAmount());
        assertEquals(new BigDecimal("0.00"), b.getDiscountAmount());
        assertEquals(new BigDecimal("100.00"), result.totalAmount());
        assertEquals(result, service.calculateSaleTotal(7L));
        verify(items, times(2)).saveAll(List.of(a, b));
    }

    @Test void removalReevaluatesRemainingPromotionsAndPersistsThem() {
        SaleItem a = line(1L, "100.00", "1"), b = line(2L, "100.00", "1");
        Sale sale = draftWithItems(new ArrayList<>(List.of(a, b))); when(sales.save(sale)).thenReturn(sale);
        when(promotions.findEffective(eq(GeneralStatus.ACTIVE), any())).thenReturn(List.of(fixedPromotion(b.getProduct(), "20")));
        var result = service.removeItemFromSale(7L, 1L);
        assertEquals(new BigDecimal("80.00"), result.totalAmount());
        verify(items).saveAll(List.of(b));
    }

    @Test void cancelSaleCancelsDraftWithTimeoutPayment() {
        Sale sale = new Sale(); sale.setSaleId(7L); sale.setStatus(SaleStatus.DRAFT);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));
        when(sales.save(any())).thenAnswer(i -> i.getArgument(0));

        Payment timeoutPayment = new Payment();
        timeoutPayment.setPaymentId(101L);
        timeoutPayment.setPaymentStatus(PaymentStatus.TIMEOUT);
        timeoutPayment.setReconciliationRequired(true);
        when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(timeoutPayment));

        SaleResponse response = service.cancelSale(7L);
        assertEquals(SaleStatus.CANCELLED, response.status());
        assertEquals(PaymentStatus.FAILED, timeoutPayment.getPaymentStatus());
        assertFalse(timeoutPayment.isReconciliationRequired());
        verify(payments).save(timeoutPayment);
        verify(sales).save(sale);
    }

    @Test void cancelSaleRejectsWhenPaymentIsSuccess() {
        Sale sale = new Sale(); sale.setSaleId(7L); sale.setStatus(SaleStatus.DRAFT);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));

        Payment successPayment = new Payment();
        successPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(successPayment));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.cancelSale(7L));
        assertTrue(ex.getMessage().contains("thanh toán thành công"));
        verify(sales, never()).save(any());
    }

    @Test void cancelSaleRejectsWhenPaymentIsActivePending() {
        Sale sale = new Sale(); sale.setSaleId(7L); sale.setStatus(SaleStatus.DRAFT);
        when(sales.findByIdForUpdate(7L)).thenReturn(Optional.of(sale));

        Payment pendingPayment = new Payment();
        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        pendingPayment.setExpiresAt(LocalDateTime.now(fixedClock).plusMinutes(10));
        when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(pendingPayment));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.cancelSale(7L));
        assertTrue(ex.getMessage().contains("chờ xử lý"));
        verify(sales, never()).save(any());
    }
}
