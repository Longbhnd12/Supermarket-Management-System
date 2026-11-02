package com.sms.backend.service.payment;

import com.sms.backend.repository.PaymentRepository;
import com.sms.backend.service.serviceImpl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.stream.LongStream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.sms.backend.entity.Payment;
import com.sms.backend.enums.PaymentMethod;
import com.sms.backend.exception.BadRequestException;

class PaymentReconcilerTest {
    @Test void repeatedQueriesAreThrottledEvenWhenTheGatewayFails() {
        var payments = mock(PaymentRepository.class);
        var service = mock(PaymentServiceImpl.class);
        var gateway = mock(PaymentGateway.class);
        var payment = new Payment(); payment.setPaymentMethod(PaymentMethod.QR);
        when(service.snapshot(1L)).thenReturn(payment);
        when(gateway.query(payment)).thenThrow(new IllegalStateException("network unavailable"));
        var reconciler = new PaymentReconciler(payments, service, gateway, Clock.systemUTC());
        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(1L));
        assertThrows(BadRequestException.class, () -> reconciler.reconcile(1L));
        verify(gateway, times(1)).query(payment);
    }
    @Test void failedGatewayQueriesDoNotStarveNewerExpiredPayments() {
        var payments = mock(PaymentRepository.class);
        var service = mock(PaymentServiceImpl.class);
        var gateway = mock(PaymentGateway.class);
        when(payments.findExpiredIds(any())).thenReturn(LongStream.rangeClosed(1, 30).boxed().toList());
        doThrow(new RuntimeException("unavailable")).when(service).expire(anyLong());
        var reconciler = new PaymentReconciler(payments, service, gateway, Clock.systemUTC());
        reconciler.sweep(); verify(service, never()).expire(26L);
        reconciler.sweep(); verify(service).expire(26L); verify(service).expire(30L);
        verifyNoInteractions(gateway);
    }
}
