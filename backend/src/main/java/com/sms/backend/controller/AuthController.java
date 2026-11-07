package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.ForgotPasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.request.ResetPasswordRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.service.AuthService;
import com.sms.backend.service.serviceImpl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * UC01 - Login. Business rules live in {@link AuthService}.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse
    ) {
        AuthenticatedUserResponse user = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Login successful", user));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedException("Authentication is required");
        }
        var user = authService.getCurrentUser(authentication.getName());
        // Effective session permissions match the authorities enforced by the filter chain.
        var permissions = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("PERM_")).map(a -> a.substring(5)).sorted().toList();
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved",
                new AuthenticatedUserResponse(user.userId(), user.username(), user.email(), user.role(), user.fullName(), permissions)));
    }

    /**
     * UC03 - Change Password. Business rules live in {@link AuthService}.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse
    ) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedException("Authentication is required");
        }
        authService.changePassword(authentication.getName(), request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Password changed", null));
    }

    /**
     * UC45 step 1 - always the same answer, whether or not the account exists (BR-03).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest
    ) {
        authService.requestPasswordReset(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(AuthServiceImpl.MSG_RESET_CODE_SENT, null));
    }

    /**
     * UC45 step 2 - check the code and set the new password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest
    ) {
        authService.resetPassword(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful", null));
    }

    /**
     * UC02 - Logout. Business rules live in {@link AuthService}.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }
}
