package com.sms.backend.service;

import com.sms.backend.dto.role.RoleResponse;
import com.sms.backend.dto.role.UpdateRolePermissionsRequest;
import com.sms.backend.entity.Role;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.Permission;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.security.RolePermissions;
import com.sms.backend.service.serviceImpl.RoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock RoleRepository roles;
    @Mock UserRepository users;
    @Mock SystemLogService logs;
    @Mock CurrentUserProvider currentUser;

    RoleServiceImpl service;

    @BeforeEach
    void setup() {
        service = new RoleServiceImpl(roles, users, logs, currentUser);
        lenient().when(currentUser.currentUser()).thenReturn(Optional.empty());
    }

    private Role role(Long id, RoleName name, String permissions) {
        Role role = new Role();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setStatus(GeneralStatus.ACTIVE);
        role.setPermissions(permissions);
        return role;
    }

    @Test
    void emptyColumnMeansDefaultPermissions() {
        Role cashier = role(3L, RoleName.CASHIER, null);
        assertTrue(RolePermissions.of(cashier).contains(Permission.POS_SALE));
        assertFalse(RolePermissions.of(cashier).contains(Permission.USER_MANAGE));
    }

    @Test
    void unknownCodesInTheDatabaseAreIgnored() {
        Role cashier = role(3L, RoleName.CASHIER, "POS_SALE, SOMETHING_OLD ,invoice_view");
        assertEquals(java.util.EnumSet.of(Permission.POS_SALE, Permission.INVOICE_VIEW), RolePermissions.of(cashier));
    }

    @Test
    void savesPermissionsAsCommaSeparatedCodesAndLogs() {
        Role cashier = role(3L, RoleName.CASHIER, null);
        when(roles.findById(3L)).thenReturn(Optional.of(cashier));

        RoleResponse result = service.updatePermissions(3L,
                new UpdateRolePermissionsRequest(List.of("POS_SALE", "INVOICE_VIEW")));

        assertEquals("INVOICE_VIEW,POS_SALE", cashier.getPermissions());
        assertEquals(List.of("INVOICE_VIEW", "POS_SALE"), result.permissions());
        verify(roles).save(cashier);
        verify(logs).createLog(isNull(), eq("ROLE_PERMISSIONS_UPDATED"), eq("ROLE"), eq(3L), anyString(), any());
    }

    @Test
    void adminCannotLoseAdministrationPermissions() {
        Role admin = role(1L, RoleName.ADMIN, null);
        when(roles.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class, () -> service.updatePermissions(1L,
                new UpdateRolePermissionsRequest(List.of("DASHBOARD_VIEW"))));
        verify(roles, never()).save(any());
    }

    @Test
    void unknownPermissionIsRejected() {
        when(roles.findById(3L)).thenReturn(Optional.of(role(3L, RoleName.CASHIER, null)));
        assertThrows(BadRequestException.class, () -> service.updatePermissions(3L,
                new UpdateRolePermissionsRequest(List.of("DELETE_EVERYTHING"))));
    }

    @Test
    void resetRestoresTheDefaultSet() {
        Role manager = role(2L, RoleName.MANAGER, "DASHBOARD_VIEW");
        when(roles.findById(2L)).thenReturn(Optional.of(manager));

        service.resetToDefault(2L);

        assertEquals(RolePermissions.defaultsFor(RoleName.MANAGER), RolePermissions.parse(manager.getPermissions()));
    }
}
