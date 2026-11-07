package com.sms.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Transports the JWT in an HttpOnly cookie (team decision).
 * <ul>
 *     <li>HttpOnly: JavaScript can never read the token (protection against XSS token theft).</li>
 *     <li>SameSite=Strict + CSRF token (SecurityConfig): protection against cross-site requests.</li>
 *     <li>Path=/api: the cookie is only sent to the REST API.</li>
 *     <li>Secure: enabled with JWT_COOKIE_SECURE=true when the site runs over HTTPS.</li>
 * </ul>
 */
@Component
public class AuthCookieService {

    static final String COOKIE_PATH = "/api";

    private final String cookieName;
    private final boolean secure;

    public AuthCookieService(@Value("${sms.jwt.cookie-name:SMS_AUTH}") String cookieName,
                             @Value("${sms.jwt.cookie-secure:false}") boolean secure) {
        this.cookieName = cookieName;
        this.secure = secure;
    }

    public void writeToken(HttpServletResponse response, String token, Duration lifetime) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, lifetime).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public String getCookieName() {
        return cookieName;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
