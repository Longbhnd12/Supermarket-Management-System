package com.sms.backend.service;

import com.sms.backend.config.VnpayProperties;
import com.sms.backend.dto.payment.*;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import com.sms.backend.service.payment.PaymentGateway;
import com.sms.backend.service.serviceImpl.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {
    @Mock SaleRepository sales;
    @Mock SaleItemRepository items;
    @Mock PaymentRepository payments;
    @Mock SaleService saleService;
    @Mock PaymentGateway gateway;
    @Mock FefoStockService stock;
    PaymentServiceImpl service;
    Sale sale;
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void setup() {
        service = new PaymentServiceImpl(sales, items, payments, saleService, new CashChangeCalculator(), gateway, new VnpayProperties(), clock, stock);
        sale = new Sale(); sale.setSaleId(1L); sale.setTotalAmount(new BigDecimal("100.00"));
        lenient().when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        lenient().when(items.findBySale(sale)).thenReturn(List.of(new SaleItem()));
        lenient().when(saleService.calculateSaleTotal(1L)).thenReturn(new SaleResponse(1L, "SALE", null, 1L, "cashier",
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), SaleStatus.DRAFT, null, null, List.of()));
        lenient().when(payments.save(any())).thenAnswer(i -> { Payment p = i.getArgument(0); p.setPaymentId(5L); return p; });
    }
    CreatePaymentRequest cashRequest() { return new CreatePaymentRequest(PaymentMethod.CASH, new BigDecimal("100"), new BigDecimal("120"), null, "request-1234"); }
    Payment attempt() {
        Payment p = new Payment(); p.setPaymentId(5L); p.setSale(sale); p.setTxnRef("ref123"); p.setAmount(new BigDecimal("100")); p.setPaymentMethod(PaymentMethod.QR);
        return p;
    }
    void callback(Payment p) {
        when(payments.findSaleIdByTxnRef("ref123")).thenReturn(Optional.of(1L));
        when(payments.findByTxnRef("ref123")).thenReturn(Optional.of(p));
    }
    PaymentGateway.GatewayResult result(boolean success, boolean failure, boolean timeout) {
        return new PaymentGateway.GatewayResult("ref123", new BigDecimal("100"), "12345", LocalDateTime.now(clock), success, failure, timeout);
    }
    @Test void cashReusesChangeCalculatorAndDoesNotCompleteSale() {
        var response = service.create(1L, cashRequest(), "127.0.0.1");
        assertEquals(PaymentStatus.SUCCESS, response.status()); assertEquals(new BigDecimal("20.00"), response.changeAmount());
        assertEquals(SaleStatus.DRAFT, sale.getStatus()); assertNull(sale.getInvoiceNumber()); assertNull(sale.getCompletedAt());
        verifyNoInteractions(gateway);
    }
    @Test void insufficientStockRejectsCashAndGatewayBeforeCreatingPayment() {
        doThrow(new BadRequestException("Insufficient FEFO stock")).when(stock).validateBeforePayment(anyList());
        for (var request : List.of(cashRequest(), new CreatePaymentRequest(PaymentMethod.QR,
                new BigDecimal("100"), null, "VNPAYQR", "request-5678"))) {
            assertThrows(BadRequestException.class, () -> service.create(1L, request, "127.0.0.1"));
        }
        verify(payments, never()).save(any()); verifyNoInteractions(gateway);
        assertEquals(SaleStatus.DRAFT, sale.getStatus()); assertFalse(sale.isCompletionRequested());
    }
    @Test void receivedAmountMustCoverTotal() {
        assertThrows(BadRequestException.class, () -> service.create(1L,
                new CreatePaymentRequest(PaymentMethod.CASH, new BigDecimal("100"), new BigDecimal("99"), null, "request-1234"), "127.0.0.1"));
        verify(payments, never()).save(any());
    }
    @Test void replayUsesOriginalAttemptWithoutCallingGatewayOrRecalculating() {
        Payment p = attempt(); p.setPaymentMethod(PaymentMethod.CASH); p.setReceivedAmount(new BigDecimal("120"));
        when(payments.findBySaleAndIdempotencyKey(sale, "request-1234")).thenReturn(Optional.of(p));
        assertEquals(5L, service.create(1L, cashRequest(), "127.0.0.1").paymentId());
        verifyNoInteractions(gateway, saleService, stock); verify(payments, never()).save(any());
    }
    @Test void replayWithDifferentBodyIsRejected() {
        Payment p = attempt();
        when(payments.findBySaleAndIdempotencyKey(sale, "request-1234")).thenReturn(Optional.of(p));
        assertThrows(BadRequestException.class, () -> service.create(1L, cashRequest(), "127.0.0.1"));
    }
    @Test void blockingPaymentRejectsAnotherAttempt() {
        when(payments.hasBlockingPayment(sale)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(1L, cashRequest(), "127.0.0.1"));
        verifyNoInteractions(gateway, saleService);
    }
    @Test void changedTotalCannotBePaid() {
        var request = new CreatePaymentRequest(PaymentMethod.CASH, new BigDecimal("99"), new BigDecimal("120"), null, "request-1234");
        assertThrows(BadRequestException.class, () -> service.create(1L, request, "127.0.0.1"));
        verify(payments, never()).save(any());
    }
    @Test void emptyOrCancelledSalesAreRejected() {
        when(items.findBySale(sale)).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.create(1L, cashRequest(), "127.0.0.1"));
        sale.setStatus(SaleStatus.CANCELLED);
        assertThrows(BadRequestException.class, () -> service.create(1L, cashRequest(), "127.0.0.1"));
    }
    @Test void electronicAttemptIsPendingWithNoCashFields() {
        when(gateway.createUrl(any(), eq("127.0.0.1"))).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test");
        var request = new CreatePaymentRequest(PaymentMethod.QR, new BigDecimal("100"), null, "VNPAYQR", "request-1234");
        var response = service.create(1L, request, "127.0.0.1");
        assertEquals(PaymentStatus.PENDING, response.status()); assertNotNull(response.paymentUrl()); assertNotNull(response.expiresAt());
        assertNull(response.paidAt()); assertNull(response.receivedAmount());
    }
    @Test void successIsIdempotentAndNeverOverwrittenByFailure() {
        Payment p = attempt(); callback(p);
        when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(p));
        assertEquals("00", service.apply(result(true, false, false)).rspCode());
        assertEquals("02", service.apply(result(false, true, false)).rspCode());
        assertEquals(PaymentStatus.SUCCESS, p.getPaymentStatus()); assertEquals(SaleStatus.DRAFT, sale.getStatus());
    }
    @Test void failureClosesOnlyAttemptAndWrongAmountDoesNotWrite() {
        Payment p = attempt(); callback(p);
        assertEquals("04", service.apply(new PaymentGateway.GatewayResult("ref123", BigDecimal.ONE, null, null, true, false, false)).rspCode());
        assertEquals(PaymentStatus.PENDING, p.getPaymentStatus());
        assertEquals("00", service.apply(result(false, true, true)).rspCode());
        assertEquals(PaymentStatus.TIMEOUT, p.getPaymentStatus()); assertFalse(p.isReconciliationRequired());
        assertEquals(SaleStatus.DRAFT, sale.getStatus()); assertNull(sale.getInvoiceNumber());
    }
    @Test void expiredWindowIsNotProofOfFailureAndLateSuccessCanResolveIt() {
        Payment p = attempt(); p.setExpiresAt(LocalDateTime.now(clock).minusMinutes(1));
        when(payments.findSaleIdByPaymentId(5L)).thenReturn(Optional.of(1L));
        when(payments.findById(5L)).thenReturn(Optional.of(p));
        service.expire(5L);
        assertEquals(PaymentStatus.TIMEOUT, p.getPaymentStatus()); assertTrue(p.isReconciliationRequired());
        callback(p); when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(p));
        service.apply(result(true, false, false));
        assertEquals(PaymentStatus.SUCCESS, p.getPaymentStatus()); assertFalse(p.isReconciliationRequired());
    }
    @Test void conflictingLatePaymentIsFlaggedAndNotRefundedAutomatically() {
        Payment p = attempt(); p.setPaymentStatus(PaymentStatus.FAILED); callback(p);
        Payment other = attempt(); other.setPaymentId(6L); other.setPaymentStatus(PaymentStatus.SUCCESS);
        when(payments.findBySaleOrderByPaymentIdDesc(sale)).thenReturn(List.of(other, p));
        service.apply(result(true, false, false));
        assertTrue(p.isReconciliationRequired()); assertTrue(other.isReconciliationRequired());
        assertEquals(PaymentStatus.SUCCESS, p.getPaymentStatus());
    }
    @Test void cashCalculatorRejectsPrecisionAndInsufficientAmount() {
        var calculator = new CashChangeCalculator();
        for (var value : Arrays.asList(null, new BigDecimal("-1"), new BigDecimal("100.001"), new BigDecimal("99999999999999"))) {
            assertThrows(BadRequestException.class, () -> calculator.calculate(new BigDecimal("100"), value));
        }
    }

    @Test void previewReturnsShortfallAndNeverWritesOrCallsGateway() {
        var result = service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("60"), new BigDecimal("100")));
        assertFalse(result.sufficient()); assertNull(result.changeAmount()); assertEquals(new BigDecimal("40.00"), result.shortfallAmount());
        assertEquals(new BigDecimal("100.00"), sale.getTotalAmount()); assertEquals(SaleStatus.DRAFT, sale.getStatus());
        verify(payments, never()).save(any()); verify(sales, never()).save(any()); verify(items, never()).save(any());
        verifyNoInteractions(gateway, saleService);
    }

    @Test void previewRejectsStaleTotalsLockedSalesAndEmptySales() {
        assertThrows(BadRequestException.class, () -> service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("120"), BigDecimal.ONE)));
        when(payments.hasBlockingPayment(sale)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("120"), new BigDecimal("100"))));
        when(payments.hasBlockingPayment(sale)).thenReturn(false); when(items.findBySale(sale)).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("120"), new BigDecimal("100"))));
        sale.setStatus(SaleStatus.COMPLETED);
        assertThrows(BadRequestException.class, () -> service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("120"), new BigDecimal("100"))));
        verify(payments, never()).save(any());
    }

    @Test void confirmationRevalidatesTotalAfterAnEarlierSuccessfulPreview() {
        assertTrue(service.previewCashChange(1L, new CashChangeRequest(new BigDecimal("120"), new BigDecimal("100"))).sufficient());
        when(saleService.calculateSaleTotal(1L)).thenReturn(new SaleResponse(1L, "SALE", null, 1L, "cashier", new BigDecimal("110"),
                BigDecimal.ZERO, new BigDecimal("110"), SaleStatus.DRAFT, null, null, List.of()));
        assertThrows(BadRequestException.class, () -> service.create(1L, cashRequest(), "127.0.0.1"));
        verify(payments, never()).save(any());
    }
}
