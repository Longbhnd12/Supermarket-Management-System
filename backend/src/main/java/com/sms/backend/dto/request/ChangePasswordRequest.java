package com.sms.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC03 - Change Password (SDD 4.2.4.1).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "The Username/Email field is required.")
        @Size(max = 150, message = "Username/Email must not exceed 150 characters")
        String identifier,

        @NotBlank(message = "The Current Password field is required.")
        @Size(max = 255)
        String currentPassword,

        @NotBlank(message = "The New Password field is required.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword,

        @NotBlank(message = "The Confirm New Password field is required.")
        String confirmPassword
) {
}
