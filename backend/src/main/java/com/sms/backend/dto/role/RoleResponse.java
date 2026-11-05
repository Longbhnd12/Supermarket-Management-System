package com.sms.backend.dto.role;

import com.sms.backend.enums.GeneralStatus;

import java.util.List;

/**
 * UC06: a role with its permission codes.
 * requiredPermissions: cannot be unchecked (BR-03, Admin only).
 */
public record RoleResponse(
        Long roleId,
        String roleName,
        String description,
        GeneralStatus status,
        List<String> permissions,
        List<String> requiredPermissions,
        long userCount
) {
}
