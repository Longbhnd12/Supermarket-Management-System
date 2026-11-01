package com.sms.backend.controller;

import com.sms.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class PurchaseOrderControllerTest {

    @Autowired
    private PurchaseOrderController purchaseOrderController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(purchaseOrderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/purchase-orders/next-number - Returns 200 with PRxxxx string")
    void testGetNextRequestNumberEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders/next-number")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", startsWith("PR")));
    }

    @Test
    @DisplayName("GET /api/v1/purchase-orders/low-stock-suggestions - Returns 200 with list of products")
    void testGetLowStockSuggestionsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders/low-stock-suggestions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders - Returns 201 Created and saves PENDING_APPROVAL request")
    void testCreatePurchaseOrderEndpoint() throws Exception {
        String futureDate = LocalDate.now().plusDays(7).toString();
        String json = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "note": "Controller integration test note",
                    "details": [
                        {
                            "productId": 1,
                            "requestedQuantity": 15,
                            "unitPrice": 12000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.poNumber", startsWith("PR")))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.totalAmount").value(180000.00))
                .andExpect(jsonPath("$.data.details[0].requestedQuantity").value(15.0));
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders/requests - Returns 201 Created (SDD 4.15.1.2.1 endpoint)")
    void testCreatePurchaseRequestEndpoint() throws Exception {
        String futureDate = LocalDate.now().plusDays(5).toString();
        String json = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "note": "SDD endpoint test",
                    "details": [
                        {
                            "productId": 6,
                            "requestedQuantity": 8,
                            "unitPrice": 20000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/v1/purchase-orders/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.totalAmount").value(160000.00));
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders - Returns 400 Bad Request when supplierId is missing")
    void testMissingSupplierValidation() throws Exception {
        String futureDate = LocalDate.now().plusDays(5).toString();
        String json = """
                {
                    "expectedDeliveryDate": "%s",
                    "details": [
                        {
                            "productId": 1,
                            "requestedQuantity": 10,
                            "unitPrice": 12000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders - Returns 400 Bad Request when items list is empty")
    void testEmptyItemsValidation() throws Exception {
        String futureDate = LocalDate.now().plusDays(5).toString();
        String json = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "details": []
                }
                """.formatted(futureDate);

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}

