package com.sms.backend.service;

import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * UC01 - Login.
 */
public interface AuthService {

    /**
     * Validates the credentials, applies the lockout rule (BR-24), starts the
     * HTTP session and returns the authenticated user with its role.
     */
    AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);
}
