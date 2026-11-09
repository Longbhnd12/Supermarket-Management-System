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
        LocalDateTime lastLoginAt,
        LocalDateTime lockedUntil,
        /* Only set by "create user": true when the login details were emailed to the new account,
           false when the email could not be sent. null in every other response. */
        Boolean credentialsEmailSent
) {
}
