package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.role.PermissionResponse;
import com.sms.backend.dto.role.RoleResponse;
import com.sms.backend.dto.role.UpdateRolePermissionsRequest;
import com.sms.backend.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC06 - Manage Roles & Permissions. Requires the ROLE_MANAGE permission (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved", roleService.getRoles()));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getPermissionCatalog() {
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved", roleService.getPermissionCatalog()));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", roleService.updatePermissions(id, request)));
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<ApiResponse<RoleResponse>> resetToDefault(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Role reset to default permissions", roleService.resetToDefault(id)));
    }
}
