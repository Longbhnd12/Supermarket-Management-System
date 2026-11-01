package com.sms.backend.service.payment;

import com.sms.backend.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentGateway {
    String createUrl(Payment payment, String customerIp);
    GatewayResult verifyIpn(Map<String, String> parameters);
    GatewayResult query(Payment payment);

    record GatewayResult(String txnRef, BigDecimal amount, String transactionNo, LocalDateTime paidAt,
                         boolean success, boolean definitiveFailure, boolean timedOut) {}
}
