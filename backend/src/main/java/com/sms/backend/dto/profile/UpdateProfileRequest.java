package com.sms.backend.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC44 - Update Profile. fullName / phone are only used when the account is linked to an employee.
 */
public record UpdateProfileRequest(
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9]{9,15}$", message = "Phone must contain 9 to 15 digits")
        String phone,

        @NotBlank(message = "The Email field is required.")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email
) {
}
