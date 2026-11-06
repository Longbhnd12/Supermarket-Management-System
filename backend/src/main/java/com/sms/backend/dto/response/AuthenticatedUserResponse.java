package com.sms.backend.dto.response;

/**
 * Current user returned by login and /auth/me.
 * fullName: employee's full name, supplier name for Supplier accounts, otherwise the username.
 */
public record AuthenticatedUserResponse(Long userId, String username, String email, String role, String fullName,
                                        java.util.List<String> permissions) {
    public AuthenticatedUserResponse(Long userId, String username, String email, String role, String fullName) {
        this(userId, username, email, role, fullName, java.util.List.of());
    }
}
