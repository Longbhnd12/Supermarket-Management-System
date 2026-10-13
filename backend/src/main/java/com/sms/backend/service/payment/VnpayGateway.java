// VnpayGateway - implementation of VNPay sandbox and production gateway
package com.sms.backend.service.payment;

import com.sms.backend.config.VnpayProperties;
import com.sms.backend.entity.Payment;
import com.sms.backend.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class VnpayGateway implements PaymentGateway {
    public static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayProperties config;
    private final Clock clock;
    private final RestClient http;

    @Autowired
    public VnpayGateway(VnpayProperties config, Clock clock) {
        this(config, clock, client());
    }
    VnpayGateway(VnpayProperties config, Clock clock, RestClient http) {
        this.config = config; this.clock = clock; this.http = http;
    }
    private static RestClient client() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4)); factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
    public boolean configured() {
        return !config.getTmnCode().isBlank() && !config.getHashSecret().isBlank()
                && validUrl(config.getReturnUrl(), false) && validUrl(config.getIpnUrl(), true);
    }
    private boolean validUrl(String value, boolean httpsOnly) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || (!httpsOnly && "http".equals(uri.getScheme())))
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }
    public boolean enabled(String bankCode) {
        return configured() && Set.of("VNPAYQR", "VNBANK", "INTCARD").contains(bankCode)
                && config.getEnabledBankCodes().contains(bankCode);
    }
    @Override
    public String createUrl(Payment payment, String customerIp) {
        if (!enabled(payment.getBankCode())) throw new BadRequestException("VNPay method is not configured/enabled for this merchant");
        var p = new TreeMap<String, String>();
        p.put("vnp_Version", "2.1.0"); p.put("vnp_Command", "pay"); p.put("vnp_TmnCode", config.getTmnCode());
        p.put("vnp_Amount", minorUnits(payment.getAmount())); p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", payment.getTxnRef()); p.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getSale().getSaleId());
        p.put("vnp_OrderType", "other"); p.put("vnp_Locale", "vn");
        if (!config.isSandboxOmitBankCode()) p.put("vnp_BankCode", payment.getBankCode());
        p.put("vnp_CreateDate", DATE.format(payment.getCreatedAt())); p.put("vnp_ExpireDate", DATE.format(payment.getExpiresAt()));
        p.put("vnp_IpAddr", customerIp); p.put("vnp_ReturnUrl", config.getReturnUrl());
        String query = canonical(p);
        return PAY_URL + "?" + query + "&vnp_SecureHash=" + sign(query);
    }
    public String minorUnits(BigDecimal amount) {
        try {
            String value = amount.movePointRight(2).toBigIntegerExact().toString();
            if (amount.signum() <= 0 || value.length() > 12) throw new ArithmeticException();
            return value;
        } catch (ArithmeticException | NullPointerException ex) {
            throw new BadRequestException("Amount is outside VNPay PAY limits");
        }
    }
    public String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("VNPay signing is not configured"); }
    }
    public static String canonical(Map<String, String> parameters) {
        var parts = new ArrayList<String>();
        new TreeMap<>(parameters).forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")
                    && value != null && !value.isEmpty()) {
                parts.add(URLEncoder.encode(key, StandardCharsets.US_ASCII) + "=" + URLEncoder.encode(value, StandardCharsets.US_ASCII));
            }
        });
        return String.join("&", parts);
    }
    private boolean sameHash(String data, String supplied) {
        if (supplied == null || !supplied.matches("[a-fA-F0-9]{128}")) return false;
        return MessageDigest.isEqual(sign(data).getBytes(StandardCharsets.US_ASCII), supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
    @Override
    public GatewayResult verifyIpn(Map<String, String> p) {
        if (!configured() || !sameHash(canonical(p), p.get("vnp_SecureHash"))
                || !config.getTmnCode().equals(p.get("vnp_TmnCode"))) {
            throw new BadRequestException("Invalid VNPay signature or merchant");
        }
        return result(p, false);
    }
    private GatewayResult result(Map<String, String> p, boolean query) {
        String ref = p.get("vnp_TxnRef"), amount = p.get("vnp_Amount"), response = p.get("vnp_ResponseCode"), status = p.get("vnp_TransactionStatus");
        if (ref == null || !ref.matches("[A-Za-z0-9_-]{1,100}") || amount == null || !amount.matches("[0-9]{1,12}")
                || response == null || status == null) throw new BadRequestException("Incomplete VNPay result");
        boolean success = "00".equals(response) && "00".equals(status);
        // 04/07 (reversal/fraud), pending and unknown responses require reconciliation, never automatic retry.
        boolean failed = "02".equals(status) && (query ? "00".equals(response)
                : Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79", "99").contains(response));
        LocalDateTime paid = null;
        String transactionNo = p.get("vnp_TransactionNo");
        if (success) {
            if (transactionNo == null || !transactionNo.matches("[0-9]{1,15}")) throw new BadRequestException("Missing VNPay transaction number");
            try { paid = LocalDateTime.parse(p.get("vnp_PayDate"), DATE); }
            catch (RuntimeException ex) { throw new BadRequestException("Invalid VNPay payment time"); }
        }
        return new GatewayResult(ref, new BigDecimal(amount).movePointLeft(2), transactionNo, paid,
                success, failed, failed && "11".equals(response));
    }
    @Override
    public GatewayResult query(Payment payment) {
        if (!configured()) throw new BadRequestException("VNPay is not configured");
        var p = new LinkedHashMap<String, String>();
        p.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", "")); p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "querydr"); p.put("vnp_TmnCode", config.getTmnCode()); p.put("vnp_TxnRef", payment.getTxnRef());
        p.put("vnp_TransactionDate", DATE.format(payment.getCreatedAt())); p.put("vnp_CreateDate", DATE.format(LocalDateTime.now(clock)));
        p.put("vnp_IpAddr", config.getServerIp()); p.put("vnp_OrderInfo", "Kiem tra giao dich " + payment.getTxnRef());
        p.put("vnp_SecureHash", sign(String.join("|", p.values())));
        Map<String, String> response = http.post().uri(QUERY_URL).contentType(MediaType.APPLICATION_JSON).body(p)
                .retrieve().body(new ParameterizedTypeReference<Map<String, String>>() {});
        if (response == null) throw new BadRequestException("Empty VNPay query response");
        String responseCode = response.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            String safeCode = responseCode != null && responseCode.matches("[0-9]{2}") ? responseCode : "missing";
            throw new BadRequestException("VNPay QUERYDR returned code " + safeCode
                    + "; payment is not confirmed. Code 94: wait 5 minutes; 91: transaction not found; 97: request checksum rejected");
        }
        String checksumData = String.join("|", List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
                "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo").stream().map(k -> Objects.toString(response.get(k), "")).toList());
        // QUERYDR adds optional promotion fields in the current spec. When present they must be signed too.
        String extendedChecksumData = checksumData + "|" + Objects.toString(response.get("vnp_PromotionCode"), "")
                + "|" + Objects.toString(response.get("vnp_PromotionAmount"), "");
        boolean hasPromotion = response.containsKey("vnp_PromotionCode") || response.containsKey("vnp_PromotionAmount");
        boolean validHash = sameHash(extendedChecksumData, response.get("vnp_SecureHash"))
                || !hasPromotion && sameHash(checksumData, response.get("vnp_SecureHash"));
        if (!validHash) throw new BadRequestException("VNPay QUERYDR response signature could not be verified; payment remains unconfirmed");
        if (!config.getTmnCode().equals(response.get("vnp_TmnCode")) || !payment.getTxnRef().equals(response.get("vnp_TxnRef"))) {
            throw new BadRequestException("VNPay QUERYDR merchant/reference mismatch; payment remains unconfirmed");
        }
        String command = response.get("vnp_Command"); // Optional in VNPay's response schema, still included as empty in signature data.
        if (command != null && !command.isBlank() && !"querydr".equals(command)) throw new BadRequestException("Unexpected VNPay QUERYDR response command");
        return result(response, true);
    }
}
