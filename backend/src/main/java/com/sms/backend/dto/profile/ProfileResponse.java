package com.sms.backend.dto.profile;

import com.sms.backend.enums.AccountStatus;

/**
 * UC44 - View/Update Profile.
 * hasEmployee = false -> Full Name / Phone are hidden and only Email is editable (BR-03).
 */
public record ProfileResponse(
        Long userId,
        String username,
        String email,
        String role,
        AccountStatus status,
        boolean hasEmployee,
        String employeeCode,
        String fullName,
        String phone,
        String position
) {
}
