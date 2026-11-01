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

    @Test
    @DisplayName("GET /api/v1/purchase-orders/pending - Returns 200 with list of pending requests")
    void testPendingPurchaseOrdersEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders/pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders/{id}/approve - Returns 200 with SENT status")
    void testApprovePurchaseOrderEndpoint() throws Exception {
        // First create a pending purchase order
        String futureDate = LocalDate.now().plusDays(4).toString();
        String createJson = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "note": "For approval test",
                    "details": [
                        {
                            "productId": 1,
                            "requestedQuantity": 10,
                            "unitPrice": 12000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        String responseStr = mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"orderId\":\\s*(\\d+)").matcher(responseStr);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "orderId should be present in response");
        long orderId = Long.parseLong(m.group(1));

        String approveJson = """
                {
                    "managerNote": "Approved by Controller Test",
                    "items": [
                        {
                            "productId": 1,
                            "approvedQuantity": 12.0
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(144000.00))
                .andExpect(jsonPath("$.data.responseReason").value("Approved by Controller Test"));
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders/{id}/reject - Returns 200 with REJECTED_BY_MANAGER status")
    void testRejectPurchaseOrderEndpoint() throws Exception {
        // Create a pending request
        String futureDate = LocalDate.now().plusDays(4).toString();
        String createJson = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "note": "For rejection test",
                    "details": [
                        {
                            "productId": 1,
                            "requestedQuantity": 5,
                            "unitPrice": 12000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        String responseStr = mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"orderId\":\\s*(\\d+)").matcher(responseStr);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "orderId should be present in response");
        long orderId = Long.parseLong(m.group(1));

        String rejectJson = """
                {
                    "reason": "Budget limit exceeded for category",
                    "managerNote": "Contact finance before re-requesting"
                }
                """;

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED_BY_MANAGER"))
                .andExpect(jsonPath("$.data.responseReason").value("Budget limit exceeded for category"));
    }

    @Test
    @DisplayName("POST /api/v1/purchase-orders/{id}/reject - Returns 400 Bad Request when reason is missing")
    void testRejectPurchaseOrderWithoutReasonValidation() throws Exception {
        String futureDate = LocalDate.now().plusDays(4).toString();
        String createJson = """
                {
                    "supplierId": 1,
                    "expectedDeliveryDate": "%s",
                    "note": "For validation test",
                    "details": [
                        {
                            "productId": 1,
                            "requestedQuantity": 5,
                            "unitPrice": 12000.00
                        }
                    ]
                }
                """.formatted(futureDate);

        String responseStr = mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"orderId\":\\s*(\\d+)").matcher(responseStr);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "orderId should be present in response");
        long orderId = Long.parseLong(m.group(1));

        String invalidRejectJson = """
                {
                    "reason": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRejectJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection reason is required"));
    }
}

