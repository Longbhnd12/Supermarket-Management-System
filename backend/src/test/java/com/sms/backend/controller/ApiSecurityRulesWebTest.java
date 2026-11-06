package com.sms.backend.controller;

import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.AuthCookieService;
import com.sms.backend.security.AuthUserLoader;
import com.sms.backend.security.JwtTokenProvider;
import com.sms.backend.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApiPermissionRules through the real Spring Security filter chain of SecurityConfig
 * (401 / 403 / 200, role + permission, deny by default). The controllers are replaced by a probe.
 */
@SpringJUnitConfig(ApiSecurityRulesWebTest.Config.class)
@WebAppConfiguration
class ApiSecurityRulesWebTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, Probe.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtTokenProvider tokenProvider() { return mock(JwtTokenProvider.class); }
        @Bean AuthCookieService cookieService() { return mock(AuthCookieService.class); }
        @Bean AuthUserLoader userLoader() { return mock(AuthUserLoader.class); }
    }

    @RestController
    static class Probe {
        @RequestMapping({"/api/v1/products", "/api/v1/products/{id}", "/api/v1/purchase-orders/{id}/approve",
                "/api/v1/payments/vnpay/ipn", "/api/v1/supplier-portal/purchase-orders", "/api/v1/system-settings",
                "/api/v1/not-in-the-table"})
        String ok() {
            return "ok";
        }
    }

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A logged-in account with a role and permission codes, like SecurityUserMapper builds it. */
    private static UserRequestPostProcessor account(String role, String... permissions) {
        SimpleGrantedAuthority[] authorities = Stream.concat(
                        Stream.of("ROLE_" + role),
                        Arrays.stream(permissions).map(permission -> "PERM_" + permission))
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return user(role.toLowerCase()).authorities(authorities);
    }

    @Test
    void notLoggedInGets401ExceptOnPublicEndpoints() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/not-in-the-table")).andExpect(status().isUnauthorized());
        // VNPay calls the IPN server-to-server, without cookie
        mvc.perform(get("/api/v1/payments/vnpay/ipn")).andExpect(status().isOk());
    }

    @Test
    void readAndWriteNeedDifferentPermissions() throws Exception {
        mvc.perform(get("/api/v1/products").with(account("CASHIER", "PRODUCT_VIEW"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/products").with(csrf()).with(account("CASHIER", "PRODUCT_VIEW")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products").with(csrf()).with(account("MANAGER", "PRODUCT_VIEW", "PRODUCT_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void approvingAPurchaseRequestNeedsTheApprovePermission() throws Exception {
        mvc.perform(post("/api/v1/purchase-orders/5/approve").with(csrf()).with(account("CASHIER", "POS_SALE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/purchase-orders/5/approve").with(csrf()).with(account("MANAGER", "PURCHASE_REQUEST_APPROVE")))
                .andExpect(status().isOk());
    }

    @Test
    void supplierStaysInThePortalEvenWithStaffPermissions() throws Exception {
        mvc.perform(get("/api/v1/products").with(account("SUPPLIER", "PRODUCT_VIEW"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/supplier-portal/purchase-orders").with(account("SUPPLIER", "SUPPLIER_PORTAL")))
                .andExpect(status().isOk());
    }

    @Test
    void systemSettingsFollowThePermissionNotTheRoleName() throws Exception {
        mvc.perform(get("/api/v1/system-settings").with(account("ADMIN", "SYSTEM_SETTINGS_MANAGE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/system-settings").with(account("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void endpointOutsideTheTableIsDeniedEvenForAdmin() throws Exception {
        mvc.perform(get("/api/v1/not-in-the-table").with(account("ADMIN", "USER_MANAGE", "SYSTEM_SETTINGS_MANAGE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/products").with(account("MANAGER", "PRODUCT_MANAGE"))).andExpect(status().isForbidden());
    }
}
