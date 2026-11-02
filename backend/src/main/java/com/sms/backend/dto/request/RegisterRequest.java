package com.sms.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may contain only letters, numbers, dots, hyphens, and underscores")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 150) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 255, message = "Password must be at least 8 characters") String password,
        @NotBlank(message = "Password confirmation is required") String confirmPassword
) {
}
