package com.sms.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Security errors in the same JSON shape as GlobalExceptionHandler (ApiErrorResponse), so the
 * frontend can tell them apart from business errors:
 * - 401 UNAUTHORIZED : not logged in / token expired or invalid -> the frontend goes back to Login
 * - 403 FORBIDDEN    : logged in but without permission (MSG19)
 * - 403 CSRF_INVALID : missing / wrong CSRF token
 * No technical details are exposed (SRS 4.2.4).
 */
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String MSG_UNAUTHORIZED = "Your session has expired or you are not logged in. Please log in again.";
    public static final String MSG_FORBIDDEN = "You do not have permission to perform this action.";
    public static final String MSG_CSRF = "Your security token is missing or expired. Please reload the page and try again.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", MSG_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        if (exception instanceof CsrfException) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_INVALID", MSG_CSRF);
        } else {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", MSG_FORBIDDEN);
        }
    }

    private void write(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":" + status
                + ",\"errorCode\":\"" + errorCode + "\",\"message\":\"" + message + "\",\"traceId\":null}");
    }
}
