package com.sms.backend.dto.payment;

import com.sms.backend.entity.Payment;
import com.sms.backend.enums.PaymentMethod;
import com.sms.backend.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long paymentId, Long saleId, PaymentMethod paymentMethod, PaymentStatus status,
        BigDecimal amount, BigDecimal receivedAmount, BigDecimal changeAmount, String txnRef,
        String gatewayTransactionId, String failureReason, boolean reconciliationRequired,
        LocalDateTime expiresAt, LocalDateTime paidAt, String paymentUrl, String idempotencyKey) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getPaymentId(), p.getSale().getSaleId(), p.getPaymentMethod(), p.getPaymentStatus(),
                p.getAmount(), p.getReceivedAmount(), p.getChangeAmount(), p.getTxnRef(), p.getGatewayTransactionId(),
                p.getFailureReason(), p.isReconciliationRequired(), p.getExpiresAt(), p.getPaidAt(),
                p.getPaymentStatus() == PaymentStatus.PENDING ? p.getPaymentUrl() : null, p.getIdempotencyKey());
    }
}
