package com.sms.backend.config;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap.admin.username:admin}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap.admin.email:admin@sms.com}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    @Bean
    public CommandLineRunner initDefaultData() {
        return args -> {
            try {
                // 1. Ensure all default roles exist and have up-to-date default permissions
                for (RoleName roleName : RoleName.values()) {
                    Role role = roleRepository.findByRoleName(roleName).orElseGet(() -> {
                        Role r = new Role();
                        r.setRoleName(roleName);
                        return r;
                    });
                    role.setStatus(GeneralStatus.ACTIVE);
                    role.setDescription("Default " + roleName.name() + " role");
                    role.setPermissions(RolePermissions.format(RolePermissions.defaultsFor(roleName)));
                    roleRepository.save(role);
                    log.info("Synchronized role & permissions: {}", roleName);
                }

                // 2. Optionally bootstrap initial Admin account if explicitly enabled and no Admin exists
                if (bootstrapAdminEnabled) {
                    bootstrapAdminAccount();
                }

            } catch (Exception e) {
                log.warn("Data initialization skipped or failed: {}", e.getMessage(), e);
            }
        };
    }

    private void bootstrapAdminAccount() {
        Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN).orElse(null);
        if (adminRole == null) {
            log.warn("Cannot bootstrap admin account: ADMIN role not found.");
            return;
        }

        // Check if an appropriate Admin account already exists (by role or by bootstrap username)
        boolean hasAdmin = userRepository.existsByRole_RoleName(RoleName.ADMIN);
        if (hasAdmin) {
            log.info("Admin account already exists in database. Skipping bootstrap admin creation.");
            return;
        }

        String username = (bootstrapAdminUsername != null && !bootstrapAdminUsername.isBlank())
                ? bootstrapAdminUsername.trim() : "admin";
        String email = (bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank())
                ? bootstrapAdminEmail.trim() : "admin@sms.com";

        if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            log.warn("Admin bootstrap is enabled, but no password was provided via environment variable APP_BOOTSTRAP_ADMIN_PASSWORD. Skipping admin creation.");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            log.info("User with username '{}' already exists. Skipping bootstrap admin creation.", username);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        admin.setRole(adminRole);
        userRepository.save(admin);
        log.info("Successfully bootstrapped initial admin account: username='{}', email='{}'", username, email);
    }
}
