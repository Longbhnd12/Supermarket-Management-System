// PaymentController - REST controller for payment creation, IPN, and status check
package com.sms.backend.controller;

import com.sms.backend.config.VnpayProperties;
import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.payment.*;
import com.sms.backend.service.PaymentService;
import com.sms.backend.service.payment.VnpayGateway;
import com.sms.backend.service.payment.PaymentReconciler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;
    private final VnpayGateway gateway;
    private final PaymentReconciler reconciler;

    @GetMapping("/methods")
    public ApiResponse<List<MethodOption>> methods() {
        return ApiResponse.success("Payment methods", List.of(
                new MethodOption("CASH", null, "Tiền mặt", true),
                new MethodOption("CARD", "VNBANK", "Thẻ / tài khoản nội địa", gateway.enabled("VNBANK")),
                new MethodOption("CARD", "INTCARD", "Thẻ quốc tế", gateway.enabled("INTCARD")),
                new MethodOption("QR", "VNPAYQR", "VNPay QR", gateway.enabled("VNPAYQR")),
                new MethodOption("EWALLET", null, "Ví điện tử — chưa có provider", false)));
    }
    public record MethodOption(String method, String bankCode, String label, boolean enabled) {}

    @PostMapping("/sales/{saleId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@PathVariable Long saleId,
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", payments.create(saleId, request, http.getRemoteAddr())));
    }
    @GetMapping("/sales/{saleId}")
    public ApiResponse<PaymentService.Overview> overview(@PathVariable Long saleId) {
        return ApiResponse.success("Payment status", payments.overview(saleId));
    }
    @PostMapping("/sales/{saleId}/cash-change")
    public ApiResponse<CashChangeResponse> previewCashChange(@PathVariable Long saleId, @Valid @RequestBody CashChangeRequest request) {
        return ApiResponse.success("Cash change calculated", payments.previewCashChange(saleId, request));
    }
    @GetMapping("/reference/{ref}")
    public ApiResponse<PaymentResponse> reference(@PathVariable String ref) {
        return ApiResponse.success("Payment status", payments.findByReference(ref));
    }
    @PostMapping("/{id}/reconcile")
    public ApiResponse<Void> reconcile(@PathVariable Long id) {
        reconciler.reconcile(id); return ApiResponse.success("Payment reconciled", null);
    }
    @PostMapping("/vnpay/confirm-cancellation")
    public ApiResponse<PaymentResponse> confirmCancellation(@RequestBody Map<String, String> parameters) {
        return ApiResponse.success("Cancellation checked", payments.confirmCancellation(parameters));
    }
    @GetMapping("/vnpay/ipn")
    public IpnResponse ipn(@RequestParam Map<String, String> parameters, HttpServletRequest request) {
        if (request.getParameterMap().values().stream().anyMatch(values -> values.length != 1))
            return new IpnResponse("97", "Duplicate parameters");
        try { return payments.ipn(parameters); }
        catch (RuntimeException ex) { return new IpnResponse("99", "Temporary processing error"); }
    }
}
