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
}
