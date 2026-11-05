package com.sms.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC45 step 2: reset code from the email + the new password.
 * identifier is the username/email entered in step 1 (kept by the screen).
 */
public record ResetPasswordRequest(
        @NotBlank(message = "The Username/Email field is required.")
        @Size(max = 150)
        String identifier,

        @NotBlank(message = "The Reset Code field is required.")
        @Size(max = 10, message = "Reset code is invalid or has expired")
        String code,

        @NotBlank(message = "The New Password field is required.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword,

        @NotBlank(message = "The Confirm New Password field is required.")
        String confirmPassword
) {
}
