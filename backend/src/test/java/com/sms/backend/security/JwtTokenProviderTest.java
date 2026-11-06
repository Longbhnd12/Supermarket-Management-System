package com.sms.backend.security;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SDD 4.2.1.2.5 JwtTokenProvider: generateToken / validateToken / getUsername / invalidateToken.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-characters";
    private static final Clock T0 = Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private SystemSettingRepository settings;
    private User user;

    @BeforeEach
    void setup() {
        settings = mock(SystemSettingRepository.class);
        when(settings.findAll()).thenReturn(List.of());
        Role role = new Role();
        role.setRoleName(RoleName.MANAGER);
        user = new User();
        user.setUserId(2L);
        user.setUsername("manager01");
        user.setRole(role);
    }

    private JwtTokenProvider providerAt(Clock clock) {
        return new JwtTokenProvider(SECRET, settings, clock);
    }

    @Test
    void generatedTokenIsValidAndCarriesUserData() {
        JwtTokenProvider provider = providerAt(T0);
        String token = provider.generateToken(user);

        assertTrue(provider.validateToken(token));
        assertEquals("manager01", provider.getUsername(token));
        JwtTokenProvider.TokenClaims claims = provider.parse(token).orElseThrow();
        assertEquals(2L, claims.userId());
        assertEquals(Duration.ofMinutes(30), Duration.between(claims.issuedAt(), claims.expiresAt()));
    }

    @Test
    void lifetimeFollowsTheSessionTimeoutSetting() {
        SystemSetting timeout = new SystemSetting();
        timeout.setSettingKey("SESSION_TIMEOUT_MINUTES");
        timeout.setSettingValue("15");
        when(settings.findAll()).thenReturn(List.of(timeout));

        assertEquals(Duration.ofMinutes(15), providerAt(T0).tokenLifetime());

        timeout.setSettingValue("999"); // out of the allowed 5-120 range -> default
        assertEquals(Duration.ofMinutes(30), providerAt(T0).tokenLifetime());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = providerAt(T0).generateToken(user);

        assertFalse(providerAt(Clock.offset(T0, Duration.ofMinutes(31))).validateToken(token));
        assertTrue(providerAt(Clock.offset(T0, Duration.ofMinutes(29))).validateToken(token));
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = providerAt(T0).generateToken(user);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        String foreign = new JwtTokenProvider("another-secret-key-that-is-at-least-32-chars", settings, T0).generateToken(user);

        JwtTokenProvider provider = providerAt(T0);
        assertFalse(provider.validateToken(tampered));
        assertFalse(provider.validateToken(foreign));
        assertFalse(provider.validateToken("not-a-jwt"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
        assertEquals("", provider.getUsername("not-a-jwt"));
    }

    @Test
    void invalidatedTokenIsRejectedButOtherTokensStillWork() {
        JwtTokenProvider provider = providerAt(T0);
        String loggedOut = provider.generateToken(user);
        String otherTab = provider.generateToken(user);

        provider.invalidateToken(loggedOut);

        assertFalse(provider.validateToken(loggedOut));
        assertTrue(provider.validateToken(otherTab));
    }

    @Test
    void passwordChangeRejectsOlderTokensOnly() {
        String before = providerAt(Clock.offset(T0, Duration.ofMinutes(-5))).generateToken(user);
        JwtTokenProvider provider = providerAt(T0);

        provider.invalidateAllTokensOf(2L);
        String after = provider.generateToken(user);

        assertFalse(provider.validateToken(before));
        assertTrue(provider.validateToken(after));
    }

    @Test
    void renewalHappensAfterHalfOfTheLifetime() {
        JwtTokenProvider.TokenClaims claims = providerAt(T0).parse(providerAt(T0).generateToken(user)).orElseThrow();

        assertFalse(providerAt(Clock.offset(T0, Duration.ofMinutes(10))).shouldRenew(claims));
        assertTrue(providerAt(Clock.offset(T0, Duration.ofMinutes(16))).shouldRenew(claims));
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("too-short", settings, T0));
    }
}
