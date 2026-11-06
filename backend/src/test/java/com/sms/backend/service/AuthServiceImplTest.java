package com.sms.backend.service;

import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.ForgotPasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.request.ResetPasswordRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.BusinessException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.serviceImpl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.sms.backend.security.AuthCookieService;
import com.sms.backend.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
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
    private static final String SECRET = "test-secret-key-that-is-at-least-32-characters";

    @Mock UserRepository users;
    @Mock SystemSettingRepository settings;
    @Mock SystemLogService logs;
    @Mock EmailService email;

    AuthServiceImpl service;
    JwtTokenProvider tokens;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setup() {
        // Real JWT provider and cookie service: the tests check the actual cookie and token
        tokens = new JwtTokenProvider(SECRET, settings, CLOCK);
        service = new AuthServiceImpl(users, settings, logs, ENCODER, tokens,
                new AuthCookieService("SMS_AUTH", false), CLOCK, email);
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

    /** Token written in the Set-Cookie header of the response, or null. */
    private String tokenInCookie(MockHttpServletResponse res) {
        String header = res.getHeader("Set-Cookie");
        if (header == null || !header.startsWith("SMS_AUTH=")) return null;
        String value = header.substring("SMS_AUTH=".length(), header.indexOf(';'));
        return value.isEmpty() ? null : value;
    }

    /** A token issued 5 minutes ago (still valid now). */
    private String oldToken(User user) {
        return new JwtTokenProvider(SECRET, settings, Clock.offset(CLOCK, Duration.ofMinutes(-5))).generateToken(user);
    }

    @Test
    void loginWithUsernameReturnsRoleAndIssuesJwtCookie() {
        User user = cashier(AccountStatus.ACTIVE, 2);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AuthenticatedUserResponse result = service.login(new LoginRequest(" cashier01 ", "Secret123"), request, response);

        assertEquals("CASHIER", result.role());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(NOW, user.getLastLoginAt());
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly"), "JavaScript must not be able to read the token");
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/api"));
        assertTrue(cookie.contains("Max-Age=1800"), "lifetime = session timeout (30 minutes)");
        JwtTokenProvider.TokenClaims claims = tokens.parse(tokenInCookie(response)).orElseThrow();
        assertEquals(7L, claims.userId());
        assertEquals("cashier01", tokens.getUsername(tokenInCookie(response)));
        assertNull(request.getSession(false), "stateless: no HttpSession");
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
        assertNull(response.getHeader("Set-Cookie"), "no token for a failed login");
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
        assertNull(response.getHeader("Set-Cookie"), "no token for a failed login");
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
        assertNull(response.getHeader("Set-Cookie"), "no token for a failed login");
    }

    // ---------- UC02 - Logout ----------

    @Test
    void currentUserShowsEmployeeFullName() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        Employee employee = new Employee();
        employee.setFullName("Nguyen Van A");
        user.setEmployee(employee);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        AuthenticatedUserResponse result = service.getCurrentUser("cashier01");

        assertEquals("Nguyen Van A", result.fullName());
        assertEquals("CASHIER", result.role());
    }

    @Test
    void currentUserWithoutEmployeeFallsBackToUsername() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.ACTIVE, 0)));
        assertEquals("cashier01", service.getCurrentUser("cashier01").fullName());
    }

    @Test
    void logoutInvalidatesTokenClearsCookieAndLogs() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));
        String token = tokens.generateToken(user);
        request.setCookies(new Cookie("SMS_AUTH", token));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("cashier01", null, List.of()));
        assertTrue(tokens.validateToken(token));

        service.logout(request, response);

        assertFalse(tokens.validateToken(token), "the token can no longer be used, even if someone copied it");
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"), "cookie deleted");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(logs).createLog(eq(user), eq("LOGOUT"), eq("USER"), eq(7L), anyString(), eq("10.0.0.5"));
    }

    @Test
    void logoutWithoutTokenDoesNotFail() {
        assertDoesNotThrow(() -> service.logout(request, response));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        verify(logs, never()).createLog(any(), anyString(), anyString(), any(), anyString(), anyString());
    }

    // ---------- UC03 - Change Password ----------

    private ChangePasswordRequest change(String identifier, String current, String newPassword, String confirm) {
        return new ChangePasswordRequest(identifier, current, newPassword, confirm);
    }

    @Test
    void changePasswordWithUsernameUpdatesHashAndLogs() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        String tokenFromAnotherDevice = oldToken(user);
        assertTrue(tokens.validateToken(tokenFromAnotherDevice));

        service.changePassword("cashier01", change("cashier01", "Secret123", "NewPass456", "NewPass456"), request, response);

        assertTrue(ENCODER.matches("NewPass456", user.getPasswordHash()));
        verify(users).save(user);
        verify(logs).createLog(eq(user), eq("PASSWORD_CHANGED"), eq("USER"), eq(7L), anyString(), eq("10.0.0.5"));
        assertFalse(tokens.validateToken(tokenFromAnotherDevice), "tokens issued before the change are rejected");
        assertTrue(tokens.validateToken(tokenInCookie(response)), "this browser gets a new valid token");
    }

    @Test
    void changePasswordAcceptsEmailCaseInsensitive() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> service.changePassword("cashier01",
                change("Cashier01@SMS.vn", "Secret123", "NewPass456", "NewPass456"), request, response));
    }

    @Test
    void changePasswordRejectsAnotherAccountsUsername() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.ACTIVE, 0)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.changePassword("cashier01",
                change("admin", "Secret123", "NewPass456", "NewPass456"), request, response));
        assertEquals(AuthServiceImpl.MSG_IDENTIFIER_MISMATCH, error.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.changePassword("cashier01",
                change("cashier01", "Wrong999", "NewPass456", "NewPass456"), request, response));
        assertEquals(AuthServiceImpl.MSG04_CURRENT_PASSWORD_INCORRECT, error.getMessage());
        verify(logs).createLog(eq(user), eq("PASSWORD_CHANGE_FAILED"), eq("USER"), eq(7L), anyString(), anyString());
        verify(users, never()).save(any());
    }

    @Test
    void changePasswordRejectsMismatchedConfirmation() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.ACTIVE, 0)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.changePassword("cashier01",
                change("cashier01", "Secret123", "NewPass456", "NewPass457"), request, response));
        assertEquals(AuthServiceImpl.MSG_CONFIRM_MISMATCH, error.getMessage());
    }

    @Test
    void changePasswordRejectsSamePassword() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.ACTIVE, 0)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.changePassword("cashier01",
                change("cashier01", "Secret123", "Secret123", "Secret123"), request, response));
        assertEquals(AuthServiceImpl.MSG_SAME_PASSWORD, error.getMessage());
    }

    // ---------- UC45 - Forgot Password ----------

    @Test
    void forgotPasswordSendsSixDigitCodeValidTenMinutes() {
        User user = cashier(AccountStatus.ACTIVE, 0);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        service.requestPasswordReset(new ForgotPasswordRequest("cashier01"), request);

        assertNotNull(user.getResetCode());
        assertTrue(user.getResetCode().matches("\\d{6}"));
        assertEquals(NOW.plusMinutes(10), user.getResetCodeExpiresAt());
        verify(email).sendPasswordResetCode("cashier01@sms.vn", "cashier01", user.getResetCode(), 10);
        verify(logs).createLog(eq(user), eq("PASSWORD_RESET_REQUESTED"), eq("USER"), eq(7L), anyString(), anyString());
    }

    @Test
    void forgotPasswordForUnknownAccountSendsNothing() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestPasswordReset(new ForgotPasswordRequest("ghost"), request));
        verifyNoInteractions(email);
        verify(users, never()).save(any());
    }

    @Test
    void forgotPasswordForInactiveAccountSendsNothing() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.INACTIVE, 0)));

        service.requestPasswordReset(new ForgotPasswordRequest("cashier01"), request);
        verifyNoInteractions(email);
    }

    @Test
    void emailFailureIsReportedToTheUser() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(cashier(AccountStatus.ACTIVE, 0)));
        doThrow(new BusinessException("Unable to send the reset code email. Please try again later."))
                .when(email).sendPasswordResetCode(anyString(), anyString(), anyString(), anyInt());

        assertThrows(BusinessException.class,
                () -> service.requestPasswordReset(new ForgotPasswordRequest("cashier01"), request));
    }

    private User withCode(AccountStatus status, String code, LocalDateTime expiresAt) {
        User user = cashier(status, 0);
        user.setResetCode(code);
        user.setResetCodeExpiresAt(expiresAt);
        return user;
    }

    @Test
    void resetWithValidCodeChangesPasswordAndInvalidatesCode() {
        User user = withCode(AccountStatus.LOCKED, "482913", NOW.plusMinutes(5));
        user.setLockedUntil(NOW.plusMinutes(10));
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));
        String tokenStillLoggedIn = oldToken(user);

        service.resetPassword(new ResetPasswordRequest("cashier01", "482913", "NewPass456", "NewPass456"), request);

        assertFalse(tokens.validateToken(tokenStillLoggedIn), "anyone logged in with the old password is signed out");

        assertTrue(ENCODER.matches("NewPass456", user.getPasswordHash()));
        assertNull(user.getResetCode());
        assertNull(user.getResetCodeExpiresAt());
        assertEquals(AccountStatus.ACTIVE, user.getStatus(), "a successful reset also removes the login lock");
        assertNull(user.getLockedUntil());
        verify(logs).createLog(eq(user), eq("PASSWORD_RESET"), eq("USER"), eq(7L), anyString(), anyString());
    }

    @Test
    void expiredCodeIsRejected() {
        User user = withCode(AccountStatus.ACTIVE, "482913", NOW.minusSeconds(1));
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.resetPassword(
                new ResetPasswordRequest("cashier01", "482913", "NewPass456", "NewPass456"), request));
        assertEquals(AuthServiceImpl.MSG_RESET_CODE_INVALID, error.getMessage());
        assertEquals(HASH, user.getPasswordHash());
    }

    @Test
    void codeIsCancelledAfterFiveWrongAttempts() {
        User user = withCode(AccountStatus.ACTIVE, "482913", NOW.plusMinutes(5));
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));
        ResetPasswordRequest wrong = new ResetPasswordRequest("cashier01", "000000", "NewPass456", "NewPass456");

        for (int i = 0; i < 4; i++) {
            assertThrows(BadRequestException.class, () -> service.resetPassword(wrong, request));
            assertEquals("482913", user.getResetCode());
        }
        assertThrows(BadRequestException.class, () -> service.resetPassword(wrong, request));
        assertNull(user.getResetCode(), "5th wrong code cancels the code");

        // even the right code no longer works
        assertThrows(BadRequestException.class, () -> service.resetPassword(
                new ResetPasswordRequest("cashier01", "482913", "NewPass456", "NewPass456"), request));
    }

    @Test
    void resetRejectsMismatchedConfirmation() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.resetPassword(
                new ResetPasswordRequest("cashier01", "482913", "NewPass456", "NewPass457"), request));
        assertEquals(AuthServiceImpl.MSG_CONFIRM_MISMATCH, error.getMessage());
    }
}
