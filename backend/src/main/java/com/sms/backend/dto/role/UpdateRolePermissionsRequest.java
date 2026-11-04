package com.sms.backend.dto.role;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * UC06: the full list of permission codes the role should have after saving.
 */
public record UpdateRolePermissionsRequest(
        @NotNull(message = "Permissions are required") List<String> permissions
) {
}
