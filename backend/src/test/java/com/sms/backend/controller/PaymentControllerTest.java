package com.sms.backend.controller;

import com.sms.backend.dto.payment.IpnResponse;
import com.sms.backend.dto.payment.CashChangeResponse;
import java.math.BigDecimal;
import com.sms.backend.exception.GlobalExceptionHandler;
import com.sms.backend.service.PaymentService;
import com.sms.backend.service.payment.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
    PaymentService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(PaymentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service, mock(VnpayGateway.class), mock(PaymentReconciler.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void ipnUsesExactProtocolEnvelopeAndRejectsDuplicateParameters() throws Exception {
        when(service.ipn(anyMap())).thenReturn(new IpnResponse("00", "Confirm Success"));
        mvc.perform(get("/api/v1/payments/vnpay/ipn").param("vnp_TxnRef", "ref123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success")).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/payments/vnpay/ipn").param("vnp_Amount", "100", "200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        verify(service, times(1)).ipn(anyMap());
    }
    @Test void invalidRequestsDoNotReachPaymentService() throws Exception {
        mvc.perform(post("/api/v1/payments/sales/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payments/sales/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"CASH\",\"expectedTotal\":0,\"idempotencyKey\":\"request-123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void unhandledIpnFailureUsesRetryableAck() throws Exception {
        when(service.ipn(anyMap())).thenThrow(new RuntimeException());
        mvc.perform(get("/api/v1/payments/vnpay/ipn")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
    }

    @Test void previewReturnsShortfallWithoutCreatingPayment() throws Exception {
        when(service.previewCashChange(eq(1L), any())).thenReturn(new CashChangeResponse(1L, new BigDecimal("64000"),
                new BigDecimal("60000"), null, new BigDecimal("4000.00"), false));
        mvc.perform(post("/api/v1/payments/sales/1/cash-change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAmount\":60000,\"expectedTotal\":64000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sufficient").value(false))
                .andExpect(jsonPath("$.data.shortfallAmount").value(4000));
        verify(service, never()).create(anyLong(), any(), anyString());
    }

    @Test void previewValidatesRequiredAmountScaleAndRange() throws Exception {
        for (String amount : new String[]{"null", "-1", "1.001", "10000000000000"}) {
            mvc.perform(post("/api/v1/payments/sales/1/cash-change").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"receivedAmount\":" + amount + ",\"expectedTotal\":64000}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
