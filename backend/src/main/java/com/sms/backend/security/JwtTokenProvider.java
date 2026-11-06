package com.sms.backend.security;

import com.sms.backend.entity.SystemSetting;
import com.sms.backend.entity.User;
import com.sms.backend.repository.SystemSettingRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDD 4.2.1.2.5 - Generates, validates and invalidates the JWT tokens used for stateless authentication.
 * <ul>
 *     <li>Signed with HMAC-SHA256 (secret from the JWT_SECRET environment variable).</li>
 *     <li>Lifetime = the session timeout system setting (default 30 minutes, allowed 5-120).</li>
 *     <li>Claims: sub = username, uid = user id, role, jti = unique token id, iat, exp.</li>
 *     <li>invalidateToken (logout) keeps the token id in memory until the token expires.</li>
 *     <li>invalidateAllTokensOf (password change / reset) rejects every token issued before that moment.</li>
 * </ul>
 * Permissions are NOT trusted from the token: they are reloaded from the database on every request
 * (see JwtAuthenticationFilter), so UC06 changes and deactivated accounts take effect immediately.
 * The invalidation lists live in memory: they are lost when the backend restarts (tokens stay short-lived).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    static final String ISSUER = "sms-backend";
    static final int DEFAULT_TIMEOUT_MINUTES = 30;
    static final int MIN_TIMEOUT_MINUTES = 5;
    static final int MAX_TIMEOUT_MINUTES = 120;

    private final SecretKey secretKey;
    private final SystemSettingRepository systemSettingRepository;
    private final Clock clock;

    /** token id (jti) -> expiry: tokens invalidated by logout, kept until they expire. */
    private final Map<String, Instant> invalidatedTokens = new ConcurrentHashMap<>();
    /** user id -> tokens issued before this instant are rejected (password changed or reset). */
    private final Map<Long, Instant> notBeforeByUser = new ConcurrentHashMap<>();

    public JwtTokenProvider(@Value("${sms.jwt.secret:}") String secret,
                            SystemSettingRepository systemSettingRepository,
                            Clock clock) {
        this.secretKey = buildKey(secret);
        this.systemSettingRepository = systemSettingRepository;
        this.clock = clock;
    }

    static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set: using a random signing key. Everyone is logged out when the backend restarts.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters (256 bits) long");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    // ---------------- SDD methods ----------------

    /** Creates a signed token containing the username and role. */
    public String generateToken(User user) {
        return issue(user).token();
    }

    /** Checks signature, expiry, and that the token has not been invalidated. */
    public boolean validateToken(String token) {
        return parse(token).isPresent();
    }

    /** Extracts the username from a token (empty string when the token is not valid). */
    public String getUsername(String token) {
        return parse(token).map(TokenClaims::username).orElse("");
    }

    /** Marks the token as invalid until its expiry time (logout). Invalid tokens are ignored. */
    public void invalidateToken(String token) {
        parse(token).ifPresent(claims -> invalidatedTokens.put(claims.tokenId(), claims.expiresAt()));
    }

    // ---------------- helpers used by the filter and AuthService ----------------

    /** Rejects every token of this user issued before now (password changed or reset). */
    public void invalidateAllTokensOf(Long userId) {
        // JWT "iat" has a precision of one second, so compare at second precision
        notBeforeByUser.put(userId, clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    public IssuedToken issue(User user) {
        String role = user.getRole() == null || user.getRole().getRoleName() == null
                ? "" : user.getRole().getRoleName().name();
        return issue(user.getUserId(), user.getUsername(), role);
    }

    public IssuedToken issue(Long userId, String username, String role) {
        Instant now = clock.instant();
        Duration lifetime = tokenLifetime();
        Instant expiresAt = now.plus(lifetime);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role == null ? "" : role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, lifetime);
    }

    /**
     * Full validation: signature, issuer, expiry, required claims, logout list and password-change cut-off.
     *
     * @return the claims, or empty when the token must not be accepted
     */
    public Optional<TokenClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number userId = claims.get(CLAIM_USER_ID, Number.class);
            if (userId == null || claims.getId() == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            TokenClaims result = new TokenClaims(userId.longValue(), claims.getSubject(), claims.getId(),
                    claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
            return isInvalidated(result) ? Optional.empty() : Optional.of(result);
        } catch (RuntimeException exception) {
            // expired, bad signature, malformed, wrong issuer... -> not authenticated
            log.debug("Rejected JWT: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    /** Sliding expiration: renew the token once half of its lifetime has passed. */
    public boolean shouldRenew(TokenClaims claims) {
        Duration lifetime = Duration.between(claims.issuedAt(), claims.expiresAt());
        return !clock.instant().isBefore(claims.issuedAt().plus(lifetime.dividedBy(2)));
    }

    public Duration tokenLifetime() {
        int minutes = systemSettingRepository.findAll().stream()
                .filter(setting -> setting.getSettingKey() != null
                        && setting.getSettingKey().toUpperCase(Locale.ROOT).contains("SESSION_TIMEOUT"))
                .map(SystemSetting::getSettingValue)
                .findFirst()
                .map(value -> {
                    try {
                        return (int) Double.parseDouble(value.trim());
                    } catch (NumberFormatException exception) {
                        return DEFAULT_TIMEOUT_MINUTES;
                    }
                })
                .filter(value -> value >= MIN_TIMEOUT_MINUTES && value <= MAX_TIMEOUT_MINUTES)
                .orElse(DEFAULT_TIMEOUT_MINUTES);
        return Duration.ofMinutes(minutes);
    }

    private boolean isInvalidated(TokenClaims claims) {
        Instant now = clock.instant();
        invalidatedTokens.values().removeIf(expiry -> expiry.isBefore(now));
        // a cut-off older than the longest possible token lifetime can no longer match anything
        notBeforeByUser.values().removeIf(cutoff -> cutoff.plus(Duration.ofMinutes(MAX_TIMEOUT_MINUTES)).isBefore(now));

        if (invalidatedTokens.containsKey(claims.tokenId())) {
            return true;
        }
        Instant notBefore = notBeforeByUser.get(claims.userId());
        return notBefore != null && claims.issuedAt().isBefore(notBefore);
    }

    /** A newly created token and how long it is valid. */
    public record IssuedToken(String token, Duration lifetime) {
    }

    /** The trusted content of a valid token. */
    public record TokenClaims(Long userId, String username, String tokenId, Instant issuedAt, Instant expiresAt) {
    }
}
