package com.sms.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class InventoryControllerTest {

    @Autowired
    private InventoryController inventoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController).build();
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
}
