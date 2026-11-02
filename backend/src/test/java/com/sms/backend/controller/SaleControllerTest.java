package com.sms.backend.controller;

import com.sms.backend.dto.sale.CreateSaleRequest;
import com.sms.backend.dto.sale.UpdateSaleItemRequest;
import java.math.BigDecimal;
import com.sms.backend.exception.GlobalExceptionHandler;
import com.sms.backend.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SaleControllerTest {
    SaleService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(SaleService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SaleController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void createReturns201AndPassesOperator() throws Exception {
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content("{\"cashierId\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        verify(service).createSale(new CreateSaleRequest(1L));
    }
    @Test void missingOrInvalidOperatorReturns400() throws Exception {
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void excessQuantityPrecisionReturns400BeforeService() throws Exception {
        mvc.perform(post("/api/v1/sales/1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"quantity\":0.0001}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void calculateUsesPostWithoutClientAmounts() throws Exception {
        mvc.perform(post("/api/v1/sales/7/calculate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).calculateSaleTotal(7L);
    }

    @Test void patchReplacesQuantityAndDeleteReturnsUpdatedSale() throws Exception {
        mvc.perform(patch("/api/v1/sales/7/items/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0.125}"))
                .andExpect(status().isOk());
        verify(service).updateItemQuantity(7L, 2L, new UpdateSaleItemRequest(new BigDecimal("0.125")));
        mvc.perform(delete("/api/v1/sales/7/items/2")).andExpect(status().isOk());
        verify(service).removeItemFromSale(7L, 2L);
    }

    @Test void patchRejectsZeroMissingAndExcessPrecisionQuantities() throws Exception {
        for (String body : new String[]{"{}", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":0.0001}"}) {
            mvc.perform(patch("/api/v1/sales/7/items/2").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
