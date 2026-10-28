package com.sms.backend.dto.user;

import com.sms.backend.enums.AccountStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for User.
 * Password hash is intentionally excluded.
 */
public record UserResponse(
        Long userId,
        Long roleId,
        String roleName,
        Long employeeId,
        String employeeFullName,
        Long supplierId,
        String supplierName,
        String username,
        String email,
        AccountStatus status,
        LocalDateTime lastLoginAt
) {
}
