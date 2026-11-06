package com.sms.backend.service;

import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.ForgotPasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.request.ResetPasswordRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * UC01 - Login, UC02 - Logout, UC03 - Change Password, UC45 - Forgot Password.
 */
public interface AuthService {

    /**
     * UC01: validates the credentials, applies the lockout rule (BR-24), issues the
     * JWT (HttpOnly cookie) and returns the authenticated user with its role.
     */
    AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Information shown in the account menu (full name, email, role).
     */
    AuthenticatedUserResponse getCurrentUser(String username);

    /**
     * UC02: records the logout, invalidates the token and deletes the auth cookie.
     */
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * UC03: the logged-in user changes their own password after confirming
     * their username/email and current password. Older tokens are invalidated; this browser gets a new one.
     */
    void changePassword(String username, ChangePasswordRequest request,
                        HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * UC45 step 1: if the account exists, creates a one-time code (valid 10 minutes)
     * and emails it. The caller always shows the same generic message (BR-03).
     */
    void requestPasswordReset(ForgotPasswordRequest request, HttpServletRequest httpRequest);

    /**
     * UC45 step 2: checks the code and sets the new password; the code is then invalidated.
     */
    void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest);
}
