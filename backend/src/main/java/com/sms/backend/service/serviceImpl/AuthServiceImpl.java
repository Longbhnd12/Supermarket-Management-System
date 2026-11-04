package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.AuthService;
import com.sms.backend.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * UC01 - Login.
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

    static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;
    static final int LOCK_DURATION_MINUTES = 15;

    private static final String ENTITY_TYPE = "USER";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final UserRepository userRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final SystemLogService systemLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;

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

        // Step 5: success -> reset counters, start the session, return the role
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        startSession(user, httpRequest, httpResponse);
        writeLog(user, "LOGIN_SUCCESS", user.getUserId(), "User logged in", ipAddress);
        return toResponse(user);
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

    private void startSession(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Prevent session fixation: give the browser a new session id after login
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        UserDetails principal = userDetailsService.loadUserByUsername(user.getUsername());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // Session timeout comes from System Settings (default 30 minutes)
        HttpSession session = httpRequest.getSession(true);
        session.setMaxInactiveInterval(sessionTimeoutMinutes() * 60);
    }

    private int maxFailedAttempts() {
        return intSetting("MAX_FAILED_LOGIN_ATTEMPTS", DEFAULT_MAX_FAILED_ATTEMPTS);
    }

    private int sessionTimeoutMinutes() {
        return intSetting("SESSION_TIMEOUT", DEFAULT_SESSION_TIMEOUT_MINUTES);
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
        return new AuthenticatedUserResponse(user.getUserId(), user.getUsername(), user.getEmail(), role);
    }
}
