package com.sms.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC45 step 1: the account (username or email) that should receive the reset code.
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "The Username/Email field is required.")
        @Size(max = 150, message = "Username/Email must not exceed 150 characters")
        String identifier
) {
}
