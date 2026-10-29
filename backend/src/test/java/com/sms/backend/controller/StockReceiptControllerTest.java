package com.sms.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class StockReceiptControllerTest {

    @Autowired
    private StockReceiptController stockReceiptController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(stockReceiptController).build();
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
}
