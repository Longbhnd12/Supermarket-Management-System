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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class StockReceiptControllerTest {

    @Autowired
    private StockReceiptController stockReceiptController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(stockReceiptController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/stock-receipts - Returns 200 with ApiResponse wrapped page")
    void testGetStockReceiptListEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/stock-receipts")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock receipt list retrieved"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/stock-receipts - Returns 400 when dateFrom is after dateTo")
    void testDateRangeValidation() throws Exception {
        mockMvc.perform(get("/api/v1/stock-receipts")
                        .param("dateFrom", "2026-10-30")
                        .param("dateTo", "2026-10-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("From Date must not be later than To Date"));
    }

    @Test
    @DisplayName("GET /api/v1/stock-receipts/{id} - Returns 200 for existing receipt")
    void testGetStockReceiptDetailEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/stock-receipts/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockReceiptId").value(1))
                .andExpect(jsonPath("$.data.receiptNumber").value("SR-TEST-001"))
                .andExpect(jsonPath("$.data.details").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/stock-receipts/{id} - Returns 404 for non-existing receipt")
    void testGetStockReceiptDetailNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/stock-receipts/99999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/stock-receipts/next-number - Returns 200 with generated receipt number")
    void testGetNextReceiptNumberEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/stock-receipts/next-number")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", startsWith("SR")));
    }

    @Test
    @DisplayName("POST /api/v1/stock-receipts - Creates receipt successfully with 201 Created")
    void testCreateStockReceiptEndpoint() throws Exception {
        String jsonPayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-CTRL-TEST-01",
                    "status": "DRAFT",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 12.00,
                            "unitCost": 25000.00
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/stock-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock receipt created successfully"))
                .andExpect(jsonPath("$.data.receiptNumber").value("SR-CTRL-TEST-01"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalCost").value(300000.00));
    }

    @Test
    @DisplayName("POST /api/v1/stock-receipts - Fails 400 when items list is empty")
    void testCreateStockReceiptEmptyItemsValidation() throws Exception {
        String jsonPayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-INVALID-EMPTY",
                    "status": "DRAFT",
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/stock-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /api/v1/stock-receipts/{id} - Updates DRAFT stock receipt successfully")
    void testUpdateStockReceiptEndpoint() throws Exception {
        String createPayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-DRAFT-TO-UPDATE",
                    "status": "DRAFT",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 5.0,
                            "unitCost": 10000.0
                        }
                    ]
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/stock-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract receiptId
        Long receiptId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.stockReceiptId")).longValue();

        String updatePayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-DRAFT-TO-UPDATE",
                    "status": "DRAFT",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 15.0,
                            "unitCost": 12000.0
                        }
                    ]
                }
                """;

        mockMvc.perform(put("/api/v1/stock-receipts/" + receiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockReceiptId").value(receiptId))
                .andExpect(jsonPath("$.data.receiptNumber").value("SR-DRAFT-TO-UPDATE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalCost").value(180000.00))
                .andExpect(jsonPath("$.data.details[0].receivedQuantity").value(15.0));
    }

    @Test
    @DisplayName("POST /api/v1/stock-receipts/{id}/cancel - Cancels DRAFT stock receipt successfully")
    void testCancelStockReceiptEndpoint() throws Exception {
        String createPayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-DRAFT-TO-CANCEL",
                    "status": "DRAFT",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 2.0,
                            "unitCost": 10000.0
                        }
                    ]
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/stock-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long receiptId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.stockReceiptId")).longValue();

        mockMvc.perform(post("/api/v1/stock-receipts/" + receiptId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockReceiptId").value(receiptId))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/stock-receipts/{id} - Rejects editing confirmed receipt with 400")
    void testCannotEditConfirmedReceiptEndpoint() throws Exception {
        String createPayload = """
                {
                    "supplierId": 1,
                    "receiptNumber": "SR-CONFIRMED-NO-EDIT",
                    "status": "CONFIRMED",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 1.0,
                            "unitCost": 10000.0
                        }
                    ]
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/stock-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long receiptId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.stockReceiptId")).longValue();

        String updatePayload = """
                {
                    "supplierId": 1,
                    "status": "DRAFT",
                    "items": [
                        {
                            "productId": 6,
                            "quantity": 2.0,
                            "unitCost": 10000.0
                        }
                    ]
                }
                """;

        mockMvc.perform(put("/api/v1/stock-receipts/" + receiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isBadRequest());
    }
}
