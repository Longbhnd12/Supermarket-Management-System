package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.request.ChangePasswordRequest;
import com.sms.backend.dto.request.LoginRequest;
import com.sms.backend.dto.request.RegisterRequest;
import com.sms.backend.dto.response.AuthenticatedUserResponse;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("Password confirmation does not match");
        }
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new BadRequestException("Username or email is already in use");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(AccountStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setRole(defaultCashierRole());
        User savedUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please sign in.", response(savedUser)));
    }

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
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved",
                authService.getCurrentUser(authentication.getName())));
    }

    /**
     * UC03 - Change Password. Business rules live in {@link AuthService}.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest
    ) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedException("Authentication is required");
        }
        authService.changePassword(authentication.getName(), request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Password changed", null));
    }

    /**
     * UC02 - Logout. Business rules live in {@link AuthService}.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    private Role defaultCashierRole() {
        return roleRepository.findByRoleName(RoleName.CASHIER)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRoleName(RoleName.CASHIER);
                    role.setStatus(GeneralStatus.ACTIVE);
                    role.setDescription("Default role for self-registered staff accounts");
                    return roleRepository.save(role);
                });
    }

    private AuthenticatedUserResponse response(User user) {
        String role = user.getRole() == null || user.getRole().getRoleName() == null
                ? "" : user.getRole().getRoleName().name();
        return new AuthenticatedUserResponse(user.getUserId(), user.getUsername(), user.getEmail(), role, user.getUsername());
    }
}
