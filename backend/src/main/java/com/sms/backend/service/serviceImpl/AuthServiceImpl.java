package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.ForgotPasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.request.ResetPasswordRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.AuthCookieService;
import com.sms.backend.security.JwtTokenProvider;
import com.sms.backend.service.AuthService;
import com.sms.backend.service.EmailService;
import com.sms.backend.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UC01 - Login, UC02 - Logout, UC03 - Change Password, UC45 - Forgot Password.
 * <ul>
 *     <li>BR-01 (SRS 3.2.1): password is validated against the BCrypt hash.</li>
 *     <li>BR-02: the role of the account decides what the user can access.</li>
 *     <li>BR-03 / AT2: only ACTIVE accounts can log in ("Account is inactive").</li>
 *     <li>BR-24: after MAX_FAILED_LOGIN_ATTEMPTS consecutive failures (default 5)
 *     the account is LOCKED for 15 minutes.</li>
 *     <li>4.2.7 Auditability: successful and failed logins are written to system_log.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    public static final String MSG03_INVALID_CREDENTIALS = "Incorrect username or password. Please check again.";
    public static final String MSG_ACCOUNT_INACTIVE = "Account is inactive. Please contact the administrator.";
    public static final String MSG_IDENTIFIER_MISMATCH = "Username/email does not match your account";
    public static final String MSG04_CURRENT_PASSWORD_INCORRECT = "Current password is incorrect.";
    public static final String MSG_CONFIRM_MISMATCH = "New password and confirmation do not match";
    public static final String MSG_SAME_PASSWORD = "New password must be different from the current password";
    public static final String MSG_RESET_CODE_SENT = "If this account exists, a reset code has been sent";
    public static final String MSG_RESET_CODE_INVALID = "Reset code is invalid or has expired";

    static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_DURATION_MINUTES = 15;
    /** SRS 4.2.4: the reset code is valid for 10 minutes and can be used only once. */
    static final int RESET_CODE_VALID_MINUTES = 10;
    /** Wrong codes allowed before the code is cancelled (protects the 6-digit code from guessing). */
    static final int MAX_RESET_CODE_ATTEMPTS = 5;

    private static final String ENTITY_TYPE = "USER";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final UserRepository userRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final SystemLogService systemLogService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;
    private final Clock clock;
    private final EmailService emailService;

    private final SecureRandom random = new SecureRandom();
    /** userId -> wrong reset codes entered for the current code (cleared when a new code is issued). */
    private final Map<Long, Integer> resetCodeAttempts = new ConcurrentHashMap<>();

    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountAccessException.class})
    public AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.identifier().trim();
        String ipAddress = clientIp(httpRequest);
        LocalDateTime now = LocalDateTime.now(clock);

        // Step 4: find the account by username OR email
        User user = findByIdentifier(identifier).orElse(null);
        if (user == null) {
            writeLog(null, "LOGIN_FAILED", null, "Unknown username/email: " + identifier, ipAddress);
            throw new UnauthorizedException(MSG03_INVALID_CREDENTIALS);
        }

        // BR-24: a lock expires by itself after 15 minutes
        releaseExpiredLock(user, now);
        if (user.getStatus() == AccountStatus.LOCKED) {
            writeLog(user, "LOGIN_BLOCKED", user.getUserId(), "Login attempt on a locked account", ipAddress);
            throw new AccountAccessException("ACCOUNT_LOCKED", lockedMessage(user));
        }

        // AT1: wrong password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = registerFailedAttempt(user, now);
            userRepository.save(user);
            if (lockedNow) {
                writeLog(user, "ACCOUNT_LOCKED", user.getUserId(),
                        "Locked for " + LOCK_DURATION_MINUTES + " minutes after too many failed logins", ipAddress);
                throw new AccountAccessException("ACCOUNT_LOCKED",
                        "Too many failed login attempts. Your account is locked until "
                                + user.getLockedUntil().format(TIME_FORMAT) + ".");
            }
            writeLog(user, "LOGIN_FAILED", user.getUserId(),
                    "Wrong password (attempt " + user.getFailedLoginAttempts() + ")", ipAddress);
            throw new UnauthorizedException(MSG03_INVALID_CREDENTIALS);
        }

        // AT2 / BR-03: correct password but the account is not active
        if (user.getStatus() != AccountStatus.ACTIVE) {
            writeLog(user, "LOGIN_FAILED", user.getUserId(), "Account is " + user.getStatus(), ipAddress);
            throw new AccountAccessException("ACCOUNT_INACTIVE", MSG_ACCOUNT_INACTIVE);
        }

        // Step 5: success -> reset counters, issue the JWT (HttpOnly cookie), return the role
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        issueToken(user, httpResponse);
        writeLog(user, "LOGIN_SUCCESS", user.getUserId(), "User logged in", ipAddress);
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user was not found"));
        return toResponse(user);
    }

    /**
     * UC02 - Logout: write the LOGOUT log, invalidate the token (SDD invalidateToken) and delete the cookie.
     * Safe to call even when the token has already expired.
     */
    @Override
    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            userRepository.findByUsername(authentication.getName())
                    .ifPresent(user -> writeLog(user, "LOGOUT", user.getUserId(), "User logged out", clientIp(httpRequest)));
        }

        authCookieService.readToken(httpRequest).ifPresent(jwtTokenProvider::invalidateToken);
        authCookieService.clear(httpResponse);
        SecurityContextHolder.clearContext();
    }

    /**
     * UC03 - Change Password (SRS 3.2.2).
     * AT1 username/email mismatch, AT2 wrong current password (MSG04), AT3 policy / confirmation,
     * BR-02 new password must differ from the current one.
     */
    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void changePassword(String username, ChangePasswordRequest request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user was not found"));
        String ipAddress = clientIp(httpRequest);

        // Step 4: the username/email typed must be the logged-in account
        String identifier = request.identifier().trim();
        boolean sameAccount = identifier.equals(user.getUsername()) || identifier.equalsIgnoreCase(user.getEmail());
        if (!sameAccount) {
            throw new BadRequestException(MSG_IDENTIFIER_MISMATCH);
        }

        // Step 5: current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            writeLog(user, "PASSWORD_CHANGE_FAILED", user.getUserId(), "Current password is incorrect", ipAddress);
            throw new BadRequestException(MSG04_CURRENT_PASSWORD_INCORRECT);
        }

        // Step 6: policy is checked by @Pattern on the request; confirmation + BR-02 here
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException(MSG_CONFIRM_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException(MSG_SAME_PASSWORD);
        }

        // Step 7: update and record the change (the password itself is never logged)
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        writeLog(user, "PASSWORD_CHANGED", user.getUserId(), "User changed their password", ipAddress);

        // Other devices / tabs logged in with the old password are signed out; this one gets a new token
        jwtTokenProvider.invalidateAllTokensOf(user.getUserId());
        issueToken(user, httpResponse);
    }

    // ---------------- UC45 - Forgot Password ----------------

    /**
     * Step 3-4. AT1 / BR-03: unknown or inactive accounts get the same generic answer
     * and no email. If the email cannot be sent, nothing is saved and the user is told
     * to try again later (SRS 4.1.3).
     */
    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String identifier = request.identifier().trim();
        String ipAddress = clientIp(httpRequest);
        User user = findByIdentifier(identifier).orElse(null);

        if (user == null || user.getStatus() == AccountStatus.INACTIVE) {
            writeLog(user, "PASSWORD_RESET_REQUESTED", user == null ? null : user.getUserId(),
                    "Reset requested for " + (user == null ? "unknown account " + identifier : "inactive account")
                            + " - no code sent", ipAddress);
            return;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        user.setResetCode(code);
        user.setResetCodeExpiresAt(LocalDateTime.now(clock).plusMinutes(RESET_CODE_VALID_MINUTES));
        userRepository.save(user);
        resetCodeAttempts.remove(user.getUserId());

        // Throws BusinessException when the email cannot be sent -> transaction rolls back
        emailService.sendPasswordResetCode(user.getEmail(), user.getUsername(), code, RESET_CODE_VALID_MINUTES);
        writeLog(user, "PASSWORD_RESET_REQUESTED", user.getUserId(), "Reset code sent to the registered email", ipAddress);
    }

    /**
     * Step 6-8. AT2: wrong / expired code -> "Reset code is invalid or has expired".
     * AT3: password policy (@Pattern) and confirmation. POS-03: the code is invalidated after use.
     * A successful reset also removes a login lock, because the user proved they own the email.
     */
    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException(MSG_CONFIRM_MISMATCH);
        }

        User user = findByIdentifier(request.identifier().trim())
                .filter(found -> found.getStatus() != AccountStatus.INACTIVE)
                .orElseThrow(() -> new BadRequestException(MSG_RESET_CODE_INVALID));
        String ipAddress = clientIp(httpRequest);
        LocalDateTime now = LocalDateTime.now(clock);

        boolean expired = user.getResetCode() == null
                || user.getResetCodeExpiresAt() == null
                || !user.getResetCodeExpiresAt().isAfter(now);
        if (expired) {
            throw new BadRequestException(MSG_RESET_CODE_INVALID);
        }

        if (!user.getResetCode().equals(request.code().trim())) {
            int attempts = resetCodeAttempts.merge(user.getUserId(), 1, Integer::sum);
            if (attempts >= MAX_RESET_CODE_ATTEMPTS) {
                // Too many wrong codes: cancel this code, the user must request a new one
                user.setResetCode(null);
                user.setResetCodeExpiresAt(null);
                userRepository.save(user);
                resetCodeAttempts.remove(user.getUserId());
                writeLog(user, "PASSWORD_RESET_FAILED", user.getUserId(), "Reset code cancelled after too many wrong attempts", ipAddress);
            }
            throw new BadRequestException(MSG_RESET_CODE_INVALID);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        if (user.getStatus() == AccountStatus.LOCKED) {
            user.setStatus(AccountStatus.ACTIVE);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        resetCodeAttempts.remove(user.getUserId());
        // Anyone still logged in with the old password is signed out
        jwtTokenProvider.invalidateAllTokensOf(user.getUserId());
        writeLog(user, "PASSWORD_RESET", user.getUserId(), "Password reset with an emailed code", ipAddress);
    }

    private Optional<User> findByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT)))
                .or(() -> userRepository.findByEmail(identifier));
    }

    private void releaseExpiredLock(User user, LocalDateTime now) {
        if (user.getStatus() == AccountStatus.LOCKED
                && user.getLockedUntil() != null
                && !user.getLockedUntil().isAfter(now)) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
    }

    /**
     * @return true when this failure locked the account
     */
    private boolean registerFailedAttempt(User user, LocalDateTime now) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);
        if (user.getStatus() == AccountStatus.ACTIVE && attempts >= maxFailedAttempts()) {
            user.setStatus(AccountStatus.LOCKED);
            user.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
            user.setFailedLoginAttempts(0);
            return true;
        }
        return false;
    }

    private String lockedMessage(User user) {
        if (user.getLockedUntil() == null) {
            return "Account is locked. Please contact the administrator.";
        }
        return "Account is locked until " + user.getLockedUntil().format(TIME_FORMAT) + ". Please try again later.";
    }

    private void issueToken(User user, HttpServletResponse httpResponse) {
        JwtTokenProvider.IssuedToken token = jwtTokenProvider.issue(user);
        authCookieService.writeToken(httpResponse, token.token(), token.lifetime());
    }

    private int maxFailedAttempts() {
        return intSetting("MAX_FAILED_LOGIN_ATTEMPTS", DEFAULT_MAX_FAILED_ATTEMPTS);
    }

    private int intSetting(String keyFragment, int defaultValue) {
        return systemSettingRepository.findAll().stream()
                .filter(setting -> setting.getSettingKey() != null
                        && setting.getSettingKey().toUpperCase(Locale.ROOT).contains(keyFragment))
                .map(SystemSetting::getSettingValue)
                .findFirst()
                .map(value -> {
                    try {
                        int number = (int) Double.parseDouble(value.trim());
                        return number > 0 ? number : defaultValue;
                    } catch (NumberFormatException exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private void writeLog(User user, String action, Long entityId, String description, String ipAddress) {
        systemLogService.createLog(user, action, ENTITY_TYPE, entityId, description, ipAddress);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }

    private AuthenticatedUserResponse toResponse(User user) {
        String role = user.getRole() == null || user.getRole().getRoleName() == null
                ? "" : user.getRole().getRoleName().name();
        return new AuthenticatedUserResponse(user.getUserId(), user.getUsername(), user.getEmail(), role, displayName(user));
    }

    private String displayName(User user) {
        if (user.getEmployee() != null && user.getEmployee().getFullName() != null) {
            return user.getEmployee().getFullName();
        }
        if (user.getSupplier() != null && user.getSupplier().getSupplierName() != null) {
            return user.getSupplier().getSupplierName();
        }
        return user.getUsername();
    }
}
