package com.sms.backend.dto.user;

import com.sms.backend.dto.request.PasswordPolicy;
import com.sms.backend.enums.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC05 - Manage Users: create an account (SRS Table 3-6).
 * employeeId: required for Manager / Cashier / Inventory Staff, optional for Admin (BR-01).
 * supplierId: required for Supplier accounts.
 */
public record CreateUserRequest(

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

        @NotBlank(message = "The Temporary Password field is required.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        AccountStatus status
) {
}
