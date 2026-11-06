package com.sms.backend.security;

import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a User into the Spring Security principal.
 * Authorities = "ROLE_&lt;role&gt;" + one "PERM_&lt;code&gt;" per effective permission (UC06, see RolePermissions).
 * Must be called inside a transaction (the role is loaded lazily).
 */
public final class SecurityUserMapper {

    private SecurityUserMapper() {
    }

    public static UserDetails toUserDetails(User user) {
        String role = user.getRole() == null || user.getRole().getRoleName() == null
                ? "ROLE_USER" : "ROLE_" + user.getRole().getRoleName().name();
        List<String> authorities = new ArrayList<>();
        authorities.add(role);
        RolePermissions.of(user.getRole()).forEach(permission -> authorities.add(RolePermissions.authority(permission)));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities.toArray(String[]::new))
                .disabled(user.getStatus() != AccountStatus.ACTIVE)
                .accountLocked(user.getStatus() == AccountStatus.LOCKED)
                .build();
    }
}
