package com.sms.backend.service.serviceImpl;

import com.sms.backend.config.VnpayProperties;
import com.sms.backend.dto.payment.*;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.*;
import com.sms.backend.repository.*;
import com.sms.backend.service.*;
import com.sms.backend.service.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final SaleRepository sales;
    private final SaleItemRepository items;
    private final PaymentRepository payments;
    private final SaleService saleService;
    private final CashChangeCalculator cash;
    private final PaymentGateway gateway;
    private final VnpayProperties config;
    private final Clock clock;
    private final FefoStockService stock;

    @Override
    @Transactional
    public CashChangeResponse previewCashChange(Long saleId, CashChangeRequest request) {
        // Same short-lived sale lock as mutations: coherent total + payment eligibility, with no writes.
        Sale sale = lockSale(saleId);
        if (sale.getStatus() != SaleStatus.DRAFT) throw new BadRequestException("Only DRAFT sales can be paid");
        if (payments.hasBlockingPayment(sale)) throw new BadRequestException("Sale is locked by a payment");
        if (items.findBySale(sale).isEmpty()) throw new BadRequestException("Cannot pay an empty sale");
        BigDecimal total = sale.getTotalAmount(); // Maintained atomically by UC24–26; do not run a write calculation for preview.
        if (request == null || request.expectedTotal() == null || total == null || total.compareTo(request.expectedTotal()) != 0)
            throw new BadRequestException("Sale total changed. Refresh and confirm the new amount");
        var result = cash.compare(total, request.receivedAmount());
        return new CashChangeResponse(saleId, total, request.receivedAmount(), result.changeAmount(), result.shortfallAmount(), result.sufficient());
    }

    @Override
    @Transactional
    public PaymentResponse create(Long saleId, CreatePaymentRequest request, String customerIp) {
        Sale sale = lockSale(saleId);
        var previous = payments.findBySaleAndIdempotencyKey(sale, request.idempotencyKey());
        if (previous.isPresent()) {
            Payment p = previous.get();
            if (p.getPaymentMethod() != request.paymentMethod() || p.getAmount().compareTo(request.expectedTotal()) != 0
                    || !Objects.equals(p.getBankCode(), normalize(request.bankCode()))
                    || !equalMoney(p.getReceivedAmount(), request.receivedAmount())) {
                throw new BadRequestException("Idempotency key was used with a different payment request");
            }
            return PaymentResponse.from(p);
        }
        if (sale.getStatus() != SaleStatus.DRAFT) throw new BadRequestException("Only DRAFT sales can be paid");
        if (payments.hasBlockingPayment(sale)) throw new BadRequestException("Sale has a pending, successful or unresolved payment");
        if (items.findBySale(sale).isEmpty()) throw new BadRequestException("Cannot pay an empty sale");
        BigDecimal amount = saleService.calculateSaleTotal(saleId).totalAmount();
        if (amount == null || amount.signum() <= 0) throw new BadRequestException("Sale total must be positive");
        if (amount.compareTo(request.expectedTotal()) != 0) throw new BadRequestException("Sale total changed. Refresh and confirm the new amount");
        if (request.paymentMethod() == PaymentMethod.EWALLET) throw new BadRequestException("EWALLET provider has not been configured");
        stock.validateBeforePayment(items.findBySale(sale));
        Payment p = new Payment(); p.setSale(sale); p.setPaymentMethod(request.paymentMethod()); p.setAmount(amount);
        p.setIdempotencyKey(request.idempotencyKey()); p.setCreatedAt(LocalDateTime.now(clock));
        p.setTxnRef(UUID.randomUUID().toString().replace("-", ""));
        if (request.paymentMethod() == PaymentMethod.CASH) {
            if (normalize(request.bankCode()) != null) throw new BadRequestException("CASH does not accept bankCode");
            p.setChangeAmount(cash.calculate(amount, request.receivedAmount())); p.setReceivedAmount(request.receivedAmount());
            p.setPaymentStatus(PaymentStatus.SUCCESS); p.setPaidAt(LocalDateTime.now(clock));
        } else {
            if (request.receivedAmount() != null) throw new BadRequestException("receivedAmount is only valid for CASH");
            String bank = normalize(request.bankCode());
            boolean allowed = request.paymentMethod() == PaymentMethod.QR ? "VNPAYQR".equals(bank)
                    : request.paymentMethod() == PaymentMethod.CARD && Set.of("VNBANK", "INTCARD").contains(bank == null ? "" : bank);
            if (!allowed) throw new BadRequestException("Invalid VNPay bankCode for selected method");
            p.setBankCode(bank); p.setExpiresAt(p.getCreatedAt().plusMinutes(Math.max(1, Math.min(60, config.getExpiryMinutes()))));
            p.setPaymentUrl(gateway.createUrl(p, customerIp));
        }
        if (p.getPaymentStatus() == PaymentStatus.SUCCESS) sale.setCompletionRequested(true);
        return PaymentResponse.from(payments.save(p));
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private boolean equalMoney(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findByReference(String ref) {
        return PaymentResponse.from(payments.findByTxnRef(ref)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found")));
    }
    @Override
    @Transactional(readOnly = true)
    public Overview overview(Long saleId) {
        Sale sale = sales.findById(saleId).orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));
        return new Overview(payments.hasBlockingPayment(sale), payments.findBySaleOrderByPaymentIdDesc(sale).stream().map(PaymentResponse::from).toList());
    }
    @Override
    @Transactional
    public IpnResponse ipn(Map<String, String> parameters) {
        PaymentGateway.GatewayResult result;
        try { result = gateway.verifyIpn(parameters); }
        catch (BadRequestException ex) { return new IpnResponse("97", "Invalid signature or input"); }
        return apply(result);
    }
    /** Called via a separate Spring bean after network I/O; locks are never held during QUERYDR. */
    @Transactional
    public IpnResponse apply(PaymentGateway.GatewayResult result) {
        Long saleId = payments.findSaleIdByTxnRef(result.txnRef()).orElse(null);
        if (saleId == null) return new IpnResponse("01", "Order not found");
        Sale sale = lockSale(saleId);
        Payment p = payments.findByTxnRef(result.txnRef()).orElseThrow();
        if (p.getPaymentMethod() == PaymentMethod.CASH) return new IpnResponse("01", "Not a gateway payment");
        if (p.getAmount().compareTo(result.amount()) != 0) return new IpnResponse("04", "Invalid amount");
        if (p.getPaymentStatus() == PaymentStatus.SUCCESS) return new IpnResponse("02", "Order already confirmed");
        // A verified late SUCCESS is financial evidence, even if an earlier result closed this attempt.
        if (result.success()) {
            p.setPaymentStatus(PaymentStatus.SUCCESS); p.setPaidAt(result.paidAt()); p.setGatewayTransactionId(result.transactionNo());
            p.setReconciliationRequired(false); p.setFailureReason(null);
            for (Payment other : payments.findBySaleOrderByPaymentIdDesc(sale)) {
                if (!Objects.equals(other.getPaymentId(), p.getPaymentId())
                        && (other.getPaymentStatus() == PaymentStatus.SUCCESS || other.getPaymentStatus() == PaymentStatus.PENDING
                        || other.isReconciliationRequired())) {
                    p.setReconciliationRequired(true); other.setReconciliationRequired(true);
                    other.setFailureReason("Late payment conflict: manual reconciliation required");
                }
            }
            if (p.isReconciliationRequired()) p.setFailureReason("Late payment conflict: manual reconciliation required");
        } else if (result.definitiveFailure()) {
            if ((p.getPaymentStatus() == PaymentStatus.FAILED || p.getPaymentStatus() == PaymentStatus.TIMEOUT) && !p.isReconciliationRequired())
                return new IpnResponse("02", "Order already confirmed");
            p.setPaymentStatus(result.timedOut() || p.getPaymentStatus() == PaymentStatus.TIMEOUT ? PaymentStatus.TIMEOUT : PaymentStatus.FAILED);
            p.setReconciliationRequired(false); p.setFailureReason("VNPay confirmed payment failure/expiry");
        } else {
            p.setReconciliationRequired(true); p.setFailureReason("Gateway result unresolved; do not retry until reconciled");
        }
        if (p.getPaymentStatus() == PaymentStatus.SUCCESS && !p.isReconciliationRequired()) sale.setCompletionRequested(true);
        payments.save(p);
        return new IpnResponse("00", "Confirm Success");
    }
    @Transactional(readOnly = true)
    public Payment snapshot(Long id) {
        Payment p = payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        // No lazy associations are needed by QUERYDR.
        return p;
    }
    @Transactional
    public void expire(Long id) {
        lockSale(payments.findSaleIdByPaymentId(id).orElseThrow());
        Payment original = payments.findById(id).orElseThrow();
        if (original.getPaymentStatus() == PaymentStatus.PENDING && original.getExpiresAt() != null
                && !original.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            original.setPaymentStatus(PaymentStatus.TIMEOUT); original.setReconciliationRequired(true);
            original.setFailureReason("Payment window expired; gateway reconciliation required before retry");
        }
    }
    private Sale lockSale(Long id) {
        return sales.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
    }
}
