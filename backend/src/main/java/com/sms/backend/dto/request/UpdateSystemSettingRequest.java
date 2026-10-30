package com.sms.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingRequest(
        @NotBlank(message = "Setting value cannot be blank")
        @Size(max = 500, message = "Setting value cannot exceed 500 characters")
        String settingValue
) {
}
