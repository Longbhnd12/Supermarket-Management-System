package com.sms.backend.dto.user;

import com.sms.backend.enums.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC05 - Manage Users: update an account. The password is not changed here.
 */
public record UpdateUserRequest(

        @NotNull(message = "The Role field is required.")
        Long roleId,

        Long employeeId,

        Long supplierId,

        @NotBlank(message = "The Username field is required.")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may contain only letters, numbers, dots, hyphens, and underscores")
        String username,

        @NotBlank(message = "The Email field is required.")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @NotNull(message = "The Status field is required.")
        AccountStatus status
) {
}
