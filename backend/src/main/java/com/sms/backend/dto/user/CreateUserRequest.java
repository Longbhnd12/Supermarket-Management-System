package com.sms.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a User account.
 * TODO (SDD): Confirm password complexity rules.
 * TODO (SDD): Confirm whether employee/supplier linkage is mandatory at creation.
 */
public record CreateUserRequest(

        @NotNull(message = "Role ID is required")
        Long roleId,

        Long employeeId,

        Long supplierId,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}
