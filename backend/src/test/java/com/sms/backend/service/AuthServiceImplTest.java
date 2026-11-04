package com.sms.backend.service;

import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.serviceImpl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String HASH = ENCODER.encode("Secret123");

    @Mock UserRepository users;
    @Mock SystemSettingRepository settings;
    @Mock SystemLogService logs;

    AuthServiceImpl service;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setup() {
        UserDetailsService details = username -> org.springframework.security.core.userdetails.User
                .withUsername(username).password(HASH).authorities("ROLE_CASHIER").build();
        service = new AuthServiceImpl(users, settings, logs, ENCODER, details,
                new HttpSessionSecurityContextRepository(), CLOCK);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        response = new MockHttpServletResponse();
        lenient().when(settings.findAll()).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User cashier(AccountStatus status, int failedAttempts) {
        Role role = new Role();
        role.setRoleName(RoleName.CASHIER);
        User user = new User();
        user.setUserId(7L);
        user.setUsername("cashier01");
        user.setEmail("cashier01@sms.vn");
        user.setPasswordHash(HASH);
        user.setRole(role);
        user.setStatus(status);
        user.setFailedLoginAttempts(failedAttempts);
        return user;
    }

    @Test
    void loginWithUsernameReturnsRoleAndStartsSession() {
        User user = cashier(AccountStatus.ACTIVE, 2);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AuthenticatedUserResponse result = service.login(new LoginRequest(" cashier01 ", "Secret123"), request, response);

        assertEquals("CASHIER", result.role());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(NOW, user.getLastLoginAt());
        assertNotNull(request.getSession(false));
        assertEquals(30 * 60, request.getSession(false).getMaxInactiveInterval());
        verify(logs).createLog(eq(user), eq("LOGIN_SUCCESS"), eq("USER"), eq(7L), anyString(), eq("10.0.0.5"));
    }

    @Test
    void loginWithEmailIsCaseInsensitive() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("Cashier01@SMS.vn")).thenReturn(Optional.empty());
        when(users.findByEmail("cashier01@sms.vn")).thenReturn(Optional.of(user));

        assertEquals("cashier01", service.login(new LoginRequest("Cashier01@SMS.vn", "Secret123"), request, response).username());
    }

    @Test
    void unknownUserGetsGenericMessage() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("ghost", "whatever"), request, response));

        assertEquals(AuthServiceImpl.MSG03_INVALID_CREDENTIALS, error.getMessage());
        verify(logs).createLog(isNull(), eq("LOGIN_FAILED"), eq("USER"), isNull(), anyString(), eq("10.0.0.5"));
    }

    @Test
    void wrongPasswordIncreasesFailedAttempts() {
        User user = cashier(AccountStatus.ACTIVE, 1);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("cashier01", "wrong"), request, response));

        assertEquals(AuthServiceImpl.MSG03_INVALID_CREDENTIALS, error.getMessage());
        assertEquals(2, user.getFailedLoginAttempts());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        verify(users).save(user);
        assertNull(request.getSession(false));
    }

    @Test
    void fifthWrongPasswordLocksAccountForFifteenMinutes() {
        User user = cashier(AccountStatus.ACTIVE, 4);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AccountAccessException error = assertThrows(AccountAccessException.class,
                () -> service.login(new LoginRequest("cashier01", "wrong"), request, response));

        assertEquals("ACCOUNT_LOCKED", error.getErrorCode());
        assertEquals(AccountStatus.LOCKED, user.getStatus());
        assertEquals(NOW.plusMinutes(15), user.getLockedUntil());
        verify(logs).createLog(eq(user), eq("ACCOUNT_LOCKED"), eq("USER"), eq(7L), anyString(), anyString());
    }

    @Test
    void lockThresholdComesFromSystemSettings() {
        SystemSetting max = new SystemSetting();
        max.setSettingKey("MAX_FAILED_LOGIN_ATTEMPTS");
        max.setSettingValue("3");
        when(settings.findAll()).thenReturn(List.of(max));
        User user = cashier(AccountStatus.ACTIVE, 2);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        assertThrows(AccountAccessException.class,
                () -> service.login(new LoginRequest("cashier01", "wrong"), request, response));
        assertEquals(AccountStatus.LOCKED, user.getStatus());
    }

    @Test
    void lockedAccountCannotLoginEvenWithCorrectPassword() {
        User user = cashier(AccountStatus.LOCKED, 0);
        user.setLockedUntil(NOW.plusMinutes(10));
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AccountAccessException error = assertThrows(AccountAccessException.class,
                () -> service.login(new LoginRequest("cashier01", "Secret123"), request, response));

        assertEquals("ACCOUNT_LOCKED", error.getErrorCode());
        assertNull(request.getSession(false));
    }

    @Test
    void expiredLockIsReleasedAutomatically() {
        User user = cashier(AccountStatus.LOCKED, 0);
        user.setLockedUntil(NOW.minusMinutes(1));
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        service.login(new LoginRequest("cashier01", "Secret123"), request, response);

        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertNull(user.getLockedUntil());
    }

    @Test
    void inactiveAccountGetsInactiveMessage() {
        User user = cashier(AccountStatus.INACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AccountAccessException error = assertThrows(AccountAccessException.class,
                () -> service.login(new LoginRequest("cashier01", "Secret123"), request, response));

        assertEquals("ACCOUNT_INACTIVE", error.getErrorCode());
        assertEquals(AuthServiceImpl.MSG_ACCOUNT_INACTIVE, error.getMessage());
        assertNull(request.getSession(false));
    }
}
