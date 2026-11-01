package com.sms.backend.service;

import com.sms.backend.dto.payment.*;
import java.util.List;
import java.util.Map;

public interface PaymentService {
    PaymentResponse create(Long saleId, CreatePaymentRequest request, String customerIp);
    CashChangeResponse previewCashChange(Long saleId, CashChangeRequest request);
    Overview overview(Long saleId);
    PaymentResponse findByReference(String ref);
    IpnResponse ipn(Map<String, String> parameters);
    PaymentResponse confirmCancellation(Map<String, String> parameters);
    record Overview(boolean locked, List<PaymentResponse> attempts) {}
}
