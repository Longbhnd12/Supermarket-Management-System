package com.sms.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username or email is required") @Size(max = 150) String identifier,
        @NotBlank(message = "Password is required") @Size(max = 255) String password
) {
}
