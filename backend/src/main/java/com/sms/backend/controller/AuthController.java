package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.identifier().trim(), request.password())
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            User user = userByUsername(authentication.getName());
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.success("Login successful", response(user)));
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException("Invalid username or password");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedException("Authentication is required");
        }
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", response(userByUsername(authentication.getName()))));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    private User userByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user was not found"));
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
        return new AuthenticatedUserResponse(user.getUserId(), user.getUsername(), user.getEmail(), role);
    }
}
