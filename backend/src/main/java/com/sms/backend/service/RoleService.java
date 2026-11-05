package com.sms.backend.service;

import com.sms.backend.dto.role.PermissionResponse;
import com.sms.backend.dto.role.RoleResponse;
import com.sms.backend.dto.role.UpdateRolePermissionsRequest;

import java.util.List;

/**
 * UC06 - Manage Roles & Permissions (only the base roles, no new roles).
 */
public interface RoleService {

    List<RoleResponse> getRoles();

    List<PermissionResponse> getPermissionCatalog();

    RoleResponse updatePermissions(Long roleId, UpdateRolePermissionsRequest request);

    RoleResponse resetToDefault(Long roleId);
}
