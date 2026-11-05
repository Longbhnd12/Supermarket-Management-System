package com.sms.backend.service;

import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * UC01 - Login, UC02 - Logout, UC03 - Change Password.
 */
public interface AuthService {

    /**
     * UC01: validates the credentials, applies the lockout rule (BR-24), starts the
     * HTTP session and returns the authenticated user with its role.
     */
    AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Information shown in the account menu (full name, email, role).
     */
    AuthenticatedUserResponse getCurrentUser(String username);

    /**
     * UC02: records the logout and ends the current session immediately.
     */
    void logout(HttpServletRequest httpRequest);

    /**
     * UC03: the logged-in user changes their own password after confirming
     * their username/email and current password. The session stays active.
     */
    void changePassword(String username, ChangePasswordRequest request, HttpServletRequest httpRequest);
}
