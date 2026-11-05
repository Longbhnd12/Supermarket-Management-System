package com.sms.backend.dto.response;

/**
 * Current user returned by login and /auth/me.
 * fullName: employee's full name, supplier name for Supplier accounts, otherwise the username.
 */
public record AuthenticatedUserResponse(Long userId, String username, String email, String role, String fullName) {
}
