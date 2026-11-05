package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.role.PermissionResponse;
import com.sms.backend.dto.role.RoleResponse;
import com.sms.backend.dto.role.UpdateRolePermissionsRequest;
import com.sms.backend.entity.Role;
import com.sms.backend.enums.Permission;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.security.RolePermissions;
import com.sms.backend.service.RoleService;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UC06 - Manage Roles & Permissions.
 * BR-02: the base roles cannot be deleted (there is no delete operation).
 * BR-03: the Admin role always keeps the permissions required for system administration.
 * POS-02: users of the role get the new permissions from their next login.
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SystemLogService systemLogService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(role -> role.getRoleName() == null ? Integer.MAX_VALUE : role.getRoleName().ordinal()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PermissionResponse> getPermissionCatalog() {
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionResponse(permission.name(), permission.getLabel(), permission.getGroup()))
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse updatePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = findRole(roleId);

        Set<Permission> requested = EnumSet.noneOf(Permission.class);
        for (String code : request.permissions()) {
            try {
                requested.add(Permission.valueOf(code.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new BadRequestException("Unknown permission: " + code);
            }
        }

        Set<Permission> required = RolePermissions.requiredFor(role.getRoleName());
        if (!requested.containsAll(required)) {
            throw new BadRequestException(
                    "Admin permissions cannot be restricted below what is required for system administration");
        }

        return save(role, requested, "Updated permissions");
    }

    @Override
    @Transactional
    public RoleResponse resetToDefault(Long roleId) {
        Role role = findRole(roleId);
        return save(role, RolePermissions.defaultsFor(role.getRoleName()), "Reset permissions to default");
    }

    private RoleResponse save(Role role, Set<Permission> permissions, String action) {
        String before = RolePermissions.format(RolePermissions.of(role));
        role.setPermissions(RolePermissions.format(permissions));
        roleRepository.save(role);

        systemLogService.createLog(
                currentUserProvider.currentUser().orElse(null),
                "ROLE_PERMISSIONS_UPDATED",
                "ROLE",
                role.getRoleId(),
                action + " of role " + role.getRoleName() + ": [" + before + "] -> [" + role.getPermissions() + "]",
                currentUserProvider.clientIp());
        return toResponse(role);
    }

    private Role findRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
    }

    private RoleResponse toResponse(Role role) {
        List<String> permissions = RolePermissions.of(role).stream().map(Permission::name).toList();
        List<String> required = RolePermissions.requiredFor(role.getRoleName()).stream().map(Permission::name).toList();
        return new RoleResponse(
                role.getRoleId(),
                role.getRoleName() == null ? null : role.getRoleName().name(),
                role.getDescription(),
                role.getStatus(),
                permissions,
                required,
                userRepository.countByRole_RoleId(role.getRoleId()));
    }
}
