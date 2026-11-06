package com.sms.backend.controller;

import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.SecurityConfig;
import com.sms.backend.service.InvoiceSearchService;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(InvoiceSearchSecurityTest.Config.class)
@WebAppConfiguration
class InvoiceSearchSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, InvoiceSearchController.class, GlobalExceptionHandler.class, Probe.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean InvoiceSearchService invoices() { return mock(InvoiceSearchService.class); }
    }
    // These probes verify the real filter chain's rules independently of UC30 rendering/data.
    @RestController
    static class Probe {
        @GetMapping({"/api/v1/sales/7/invoice", "/api/v1/sales/7/invoice/pdf", "/api/v1/sales"})
        String read() { return "ok"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired InvoiceSearchService service;
    MockMvc mvc;
    static final String[] PATHS = {"/api/v1/invoices", "/api/v1/sales/7/invoice", "/api/v1/sales/7/invoice/pdf"};

    @BeforeEach void setup() {
        reset(service);
        when(service.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void anonymousCannotReadInvoices() throws Exception {
        for (String path : PATHS) mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void eachAllowedRoleWithPermissionCanReadAllInvoiceEndpoints() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER", "CASHIER")) {
            for (String path : PATHS) mvc.perform(get(path).with(user("operator").authorities(
                    new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority("PERM_INVOICE_VIEW"))))
                    .andExpect(status().isOk());
        }
    }
    @Test void roleAndPermissionAreBothRequired() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER", "CASHIER", "WAREHOUSE_STAFF", "SUPPLIER")) {
            for (String path : PATHS) mvc.perform(get(path).with(user("operator").roles(role)))
                    .andExpect(status().isForbidden());
        }
        for (String role : List.of("WAREHOUSE_STAFF", "SUPPLIER")) {
            for (String path : PATHS) mvc.perform(get(path).with(user("operator").authorities(
                    new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority("PERM_INVOICE_VIEW"))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void cashierDraftLookupRemainsAccessible() throws Exception {
        mvc.perform(get("/api/v1/sales").param("status", "DRAFT").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
    }
    @Test void searchParsesCriteriaAndRejectsMalformedParameters() throws Exception {
        var operator = user("cashier").authorities(new SimpleGrantedAuthority("ROLE_CASHIER"), new SimpleGrantedAuthority("PERM_INVOICE_VIEW"));
        mvc.perform(get("/api/v1/invoices").param("invoiceNumber", "INV-1").param("fromDate", "2026-11-05")
                .param("page", "2").param("size", "10").with(operator)).andExpect(status().isOk());
        verify(service).search("INV-1", java.time.LocalDate.of(2026, 11, 5), null, 2, 10);
        for (String value : List.of("invalid", "2026-02-30")) mvc.perform(get("/api/v1/invoices")
                .param("fromDate", value).with(operator)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/invoices").param("page", "abc").with(operator)).andExpect(status().isBadRequest());
    }
}
