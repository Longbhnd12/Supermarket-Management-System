package com.sms.backend.service.payment;

import com.sms.backend.config.VnpayProperties;
import com.sms.backend.entity.*;
import com.sms.backend.exception.BadRequestException;
import org.junit.jupiter.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewayTest {
    VnpayProperties config;
    VnpayGateway gateway;
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void setup() {
        config = new VnpayProperties(); config.setTmnCode("TESTCODE"); config.setHashSecret("test-only-secret");
        config.setReturnUrl("http://localhost:5173/payments/vnpay/return"); config.setIpnUrl("https://test.invalid/api/v1/payments/vnpay/ipn");
        config.setEnabledBankCodes(Set.of("VNPAYQR", "VNBANK", "INTCARD")); gateway = new VnpayGateway(config, clock);
    }
    Payment attempt() {
        Payment p = new Payment(); Sale s = new Sale(); s.setSaleId(1L); p.setSale(s); p.setTxnRef("testref");
        p.setAmount(new BigDecimal("10000")); p.setBankCode("VNPAYQR"); p.setCreatedAt(LocalDateTime.now(clock)); p.setExpiresAt(p.getCreatedAt().plusMinutes(15)); return p;
    }
    Map<String, String> callback() {
        var p = new HashMap<String, String>(); p.put("vnp_TmnCode", "TESTCODE"); p.put("vnp_TxnRef", "testref");
        p.put("vnp_Amount", "1000000"); p.put("vnp_ResponseCode", "00"); p.put("vnp_TransactionStatus", "00");
        p.put("vnp_TransactionNo", "123456"); p.put("vnp_PayDate", "20261030100500");
        p.put("vnp_SecureHash", gateway.sign(VnpayGateway.canonical(p))); return p;
    }
    @Test void signatureUsesHmacSha512KnownVectorAndEncodedSortedFields() {
        config.setHashSecret("key");
        assertEquals("b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a",
                gateway.sign("The quick brown fox jumps over the lazy dog"));
        assertEquals("vnp_A=a%2Bb&vnp_Z=two+words", VnpayGateway.canonical(Map.of("vnp_Z", "two words", "vnp_A", "a+b", "vnp_SecureHash", "excluded")));
    }
    @Test void createsSignedSandboxUrlWithAmountAndGmtPlusSevenTime() {
        String url = gateway.createUrl(attempt(), "127.0.0.1");
        assertTrue(url.startsWith(VnpayGateway.PAY_URL)); assertTrue(url.contains("vnp_Amount=1000000"));
        assertTrue(url.contains("vnp_CreateDate=20261030100000")); assertTrue(url.contains("vnp_ExpireDate=20261030101500"));
        assertTrue(url.contains("vnp_BankCode=VNPAYQR")); assertFalse(url.contains("vnp_SecureHashType"));
        assertThrows(BadRequestException.class, () -> gateway.minorUnits(new BigDecimal("10000000000")));
        assertThrows(BadRequestException.class, () -> gateway.minorUnits(new BigDecimal("1.001")));
    }
    @Test void signatureAndMerchantMustMatchAndBothStatusCodesMustSucceed() {
        var p = callback(); assertTrue(gateway.verifyIpn(p).success());
        p.put("vnp_Amount", "1"); assertThrows(BadRequestException.class, () -> gateway.verifyIpn(p));
        var suspicious = callback(); suspicious.put("vnp_TransactionStatus", "07"); suspicious.put("vnp_SecureHash", gateway.sign(VnpayGateway.canonical(suspicious)));
        assertFalse(gateway.verifyIpn(suspicious).success()); assertFalse(gateway.verifyIpn(suspicious).definitiveFailure());
    }
    @Test void disabledMerchantHasNoDemoFallback() {
        config.setHashSecret(""); assertFalse(gateway.configured());
        assertThrows(BadRequestException.class, () -> gateway.createUrl(attempt(), "127.0.0.1"));
    }
    @Test void realHttpQueryRejectsUpstreamServerErrorWithoutInventingFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("querydr")); assertTrue(body.contains("vnp_TransactionDate"));
            exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        server.start();
        try {
            var factory = new SimpleClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(1));
            var client = RestClient.builder().requestFactory((uri, method) -> factory.createRequest(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/query"), method)).build();
            var testGateway = new VnpayGateway(config, clock, client);
            assertThrows(org.springframework.web.client.RestClientResponseException.class, () -> testGateway.query(attempt()));
        } finally { server.stop(0); }
    }

    @Test void realHttpQueryValidatesSignedResponseAndSerializedRequest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var response = new LinkedHashMap<String, String>();
        response.put("vnp_ResponseId", "response123"); response.put("vnp_Command", "querydr"); response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success"); response.put("vnp_TmnCode", "TESTCODE"); response.put("vnp_TxnRef", "testref");
        response.put("vnp_Amount", "1000000"); response.put("vnp_BankCode", "NCB"); response.put("vnp_PayDate", "20261030100500");
        response.put("vnp_TransactionNo", "123456"); response.put("vnp_TransactionType", "01"); response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Thanh toan");
        response.put("vnp_SecureHash", gateway.sign(String.join("|", response.values())));
        server.createContext("/query", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = new tools.jackson.databind.ObjectMapper().writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var factory = new SimpleClientHttpRequestFactory();
            var client = RestClient.builder().requestFactory((uri, method) -> factory.createRequest(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/query"), method)).build();
            var testGateway = new VnpayGateway(config, clock, client);
            assertTrue(testGateway.query(attempt()).success());
            var sent = new tools.jackson.databind.ObjectMapper().readTree(captured.get());
            String requestData = String.join("|", List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TxnRef",
                    "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo").stream().map(k -> sent.get(k).asText()).toList());
            assertEquals(gateway.sign(requestData), sent.get("vnp_SecureHash").asText());
            response.remove("vnp_SecureHash");
            response.put("vnp_PromotionCode", "TESTPROMO"); response.put("vnp_PromotionAmount", "0");
            response.put("vnp_SecureHash", gateway.sign(String.join("|", response.values())));
            assertTrue(testGateway.query(attempt()).success());
            response.put("vnp_PromotionCode", "TAMPERED");
            assertThrows(BadRequestException.class, () -> testGateway.query(attempt()));
            response.put("vnp_Amount", "2000000");
            assertThrows(BadRequestException.class, () -> testGateway.query(attempt()));
        } finally { server.stop(0); }
    }

    @Test void realHttpTimeoutDoesNotCreateAFailedFinancialResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var factory = new SimpleClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofMillis(60));
            var client = RestClient.builder().requestFactory((uri, method) -> factory.createRequest(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/query"), method)).build();
            var error = assertThrows(org.springframework.web.client.RestClientException.class,
                    () -> new VnpayGateway(config, clock, client).query(attempt()));
            Throwable root = error;
            while (root.getCause() != null) root = root.getCause();
            assertInstanceOf(java.net.SocketTimeoutException.class, root);
        } finally { server.stop(0); }
    }
}
