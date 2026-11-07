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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class InventoryControllerTest {

    @Autowired
    private InventoryController inventoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Returns 200 with ApiResponse wrapped page")
    void testGetInventoryListEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Inventory list retrieved"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/inventory?keyword=Coca - Filters correctly")
    void testGetInventoryListKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")
                        .param("keyword", "Coca")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productName").value("Coca Cola 330ml"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/export - Returns CSV attachment")
    void testExportInventoryCsvEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"inventory.csv\"")))
                .andExpect(content().string(containsString("SKU,Barcode,Product Name,Category,On Hand,Safety Min,Status")));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/adjust - Successfully adjusts stock and returns 200")
    void testAdjustStockEndpointSuccess() throws Exception {
        String jsonPayload = """
                {
                    "productId": 6,
                    "adjustmentType": "INCREASE",
                    "adjustmentQuantity": 10,
                    "reason": "Routine shelf adjustment"
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock adjusted successfully"))
                .andExpect(jsonPath("$.data.productId").value(6))
                .andExpect(jsonPath("$.data.adjustmentType").value("INCREASE"))
                .andExpect(jsonPath("$.data.adjustmentQuantity").value(10))
                .andExpect(jsonPath("$.data.reason").value("Routine shelf adjustment"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/adjust - Fails 400 when reason is missing")
    void testAdjustStockEndpointMissingReason() throws Exception {
        String jsonPayload = """
                {
                    "productId": 6,
                    "adjustmentType": "INCREASE",
                    "adjustmentQuantity": 10,
                    "reason": ""
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // UC34 - Stock History Controller Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/inventory/history - Returns 200 with paginated stock movement history")
    void testGetStockHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/history")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock history retrieved"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/history?productId=1 - Filters by product")
    void testGetStockHistoryByProductEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/history")
                        .param("productId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/history?startDate=2026-12-31&endDate=2026-01-01 - Returns 400 Bad Request (AT3)")
    void testGetStockHistoryInvalidDateRangeEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/history")
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-01-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start date cannot be after end date"));
    }

    // =========================================================================
    // UC35 - Stock Alerts Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/inventory/alerts - Returns 200 with StockAlertSummaryResponse")
    void testGetStockAlertsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Stock alerts retrieved"))
                .andExpect(jsonPath("$.data.lowStockCount").isNumber())
                .andExpect(jsonPath("$.data.outOfStockCount").isNumber())
                .andExpect(jsonPath("$.data.nearExpiryCount").isNumber())
                .andExpect(jsonPath("$.data.expiredCount").isNumber())
                .andExpect(jsonPath("$.data.totalAlerts").isNumber())
                .andExpect(jsonPath("$.data.alerts").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/stock-alerts?alertType=LOW_STOCK - Filters by alertType")
    void testGetStockAlertsFilteredByTypeEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/stock-alerts")
                        .param("alertType", "LOW_STOCK")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.alerts").isArray());
    }

    // =========================================================================
    // UC32 - Stock Check & Stock Difference Controller Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/inventory/checks/next-session - Generates valid next session number")
    void testGetNextStockCheckSessionEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/checks/next-session")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", startsWith("SC-")));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/checks - Creates stock check session and returns 201 Created")
    void testPerformStockCheckEndpointSuccess() throws Exception {
        String jsonPayload = """
                {
                    "checkNumber": "SC-TEST-001",
                    "items": [
                        {
                            "productId": 1,
                            "actualQuantity": 150,
                            "reason": "Normal physical count"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkNumber").value("SC-TEST-001"))
                .andExpect(jsonPath("$.data.totalSkus").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productId").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/checks - Fails 400 when items are empty")
    void testPerformStockCheckEndpointEmptyItems() throws Exception {
        String jsonPayload = """
                {
                    "checkNumber": "SC-EMPTY-001",
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one product item must be counted"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/checks/session/{checkNumber} - Returns session details")
    void testGetStockCheckSessionEndpoint() throws Exception {
        // First create a session
        String jsonPayload = """
                {
                    "checkNumber": "SC-FETCH-001",
                    "items": [
                        {
                            "productId": 1,
                            "actualQuantity": 100,
                            "reason": "Initial test check"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated());

        // Then retrieve session
        mockMvc.perform(get("/api/v1/inventory/checks/session/SC-FETCH-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkNumber").value("SC-FETCH-001"))
                .andExpect(jsonPath("$.data.totalSkus").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/checks/sessions - Returns list of audit sessions")
    void testGetAllStockCheckSessionsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/checks/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/checks/{id} - Updates physical count for unreconciled item")
    void testUpdateStockCheckCountEndpoint() throws Exception {
        String sessionPayload = """
                {
                    "checkNumber": "SC-UPDATE-001",
                    "items": [
                        {
                            "productId": 1,
                            "actualQuantity": 50,
                            "reason": "Initial miscount"
                        }
                    ]
                }
                """;

        String postResponse = mockMvc.perform(post("/api/v1/inventory/checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract stockCheckId
        Integer stockCheckId = com.jayway.jsonpath.JsonPath.read(postResponse, "$.data.items[0].stockCheckId");

        String updatePayload = """
                {
                    "actualQuantity": 125,
                    "reason": "Corrected physical count"
                }
                """;

        mockMvc.perform(put("/api/v1/inventory/checks/" + stockCheckId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actualQuantity").value(125))
                .andExpect(jsonPath("$.data.reason").value("Corrected physical count"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/products/{productId}/batches - Returns 200 with list of confirmed batches")
    void testGetProductBatchesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/products/1/batches")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product batches retrieved"))
                .andExpect(jsonPath("$.data").isArray());
    }
}
