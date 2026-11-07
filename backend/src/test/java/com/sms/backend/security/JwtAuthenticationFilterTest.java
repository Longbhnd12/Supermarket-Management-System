package com.sms.backend.security;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.SystemSettingRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-characters";
    private static final Clock T0 = Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private SystemSettingRepository settings;
    private AuthUserLoader userLoader;
    private AuthCookieService cookies;
    private User user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityContextRepository contextRepository;

    @BeforeEach
    void setup() {
        settings = mock(SystemSettingRepository.class);
        when(settings.findAll()).thenReturn(List.of());
        userLoader = mock(AuthUserLoader.class);
        cookies = new AuthCookieService("SMS_AUTH", false);
        Role role = new Role();
        role.setRoleName(RoleName.CASHIER);
        user = new User();
        user.setUserId(7L);
        user.setUsername("cashier01");
        user.setRole(role);
        request = new MockHttpServletRequest("GET", "/api/v1/products");
        response = new MockHttpServletResponse();
        contextRepository = new RequestAttributeSecurityContextRepository();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private JwtTokenProvider provider(Clock clock) {
        return new JwtTokenProvider(SECRET, settings, clock);
    }

    private UserDetails cashierDetails() {
        return org.springframework.security.core.userdetails.User.withUsername("cashier01").password("x")
                .authorities("ROLE_CASHIER", "PERM_POS_SALE").build();
    }

    private Authentication runFilter(JwtTokenProvider provider) throws Exception {
        Authentication[] seen = new Authentication[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen[0] = SecurityContextHolder.getContext().getAuthentication();
            }
        };
        new JwtAuthenticationFilter(provider, cookies, userLoader, contextRepository).doFilter(request, response, chain);
        return seen[0];
    }

    @Test
    void validCookieAuthenticatesWithPermissionsFromTheDatabase() throws Exception {
        JwtTokenProvider provider = provider(T0);
        request.setCookies(new Cookie("SMS_AUTH", provider.generateToken(user)));
        when(userLoader.loadActiveUser(7L)).thenReturn(Optional.of(cashierDetails()));

        Authentication authentication = runFilter(provider);

        assertNotNull(authentication);
        assertEquals("cashier01", authentication.getName());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("PERM_POS_SALE")));
        assertNull(response.getHeader("Set-Cookie"), "fresh token: no renewal");
    }

    @Test
    void authenticationIsSavedSoSessionManagementDoesNotRotateTheCsrfCookie() throws Exception {
        JwtTokenProvider provider = provider(T0);
        request.setCookies(new Cookie("SMS_AUTH", provider.generateToken(user)));
        when(userLoader.loadActiveUser(7L)).thenReturn(Optional.of(cashierDetails()));

        runFilter(provider);

        // SessionManagementFilter skips CsrfAuthenticationStrategy when the repository has the context
        assertTrue(contextRepository.containsContext(request));
    }

    @Test
    void anonymousRequestIsNotSaved() throws Exception {
        runFilter(provider(T0));
        assertFalse(contextRepository.containsContext(request));
    }

    @Test
    void noCookieStaysAnonymous() throws Exception {
        assertNull(runFilter(provider(T0)));
        verifyNoInteractions(userLoader);
    }

    @Test
    void garbageTokenIsClearedAndStaysAnonymous() throws Exception {
        request.setCookies(new Cookie("SMS_AUTH", "garbage"));

        assertNull(runFilter(provider(T0)));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }

    @Test
    void deactivatedAccountIsRejectedImmediately() throws Exception {
        JwtTokenProvider provider = provider(T0);
        request.setCookies(new Cookie("SMS_AUTH", provider.generateToken(user)));
        when(userLoader.loadActiveUser(7L)).thenReturn(Optional.empty()); // INACTIVE / LOCKED / deleted

        assertNull(runFilter(provider));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }

    @Test
    void loggedOutTokenIsRejected() throws Exception {
        JwtTokenProvider provider = provider(T0);
        String token = provider.generateToken(user);
        provider.invalidateToken(token);
        request.setCookies(new Cookie("SMS_AUTH", token));

        assertNull(runFilter(provider));
        verifyNoInteractions(userLoader);
    }

    @Test
    void activeUserGetsARenewedTokenAfterHalfOfTheLifetime() throws Exception {
        String token = provider(T0).generateToken(user);
        JwtTokenProvider later = provider(Clock.offset(T0, Duration.ofMinutes(20)));
        request.setCookies(new Cookie("SMS_AUTH", token));
        when(userLoader.loadActiveUser(7L)).thenReturn(Optional.of(cashierDetails()));

        assertNotNull(runFilter(later));
        String renewed = response.getHeader("Set-Cookie");
        assertNotNull(renewed);
        assertTrue(renewed.contains("Max-Age=1800"));
    }
}
