package com.sms.backend.config;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DataInitializerUnitTest {

    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        when(passwordEncoder.encode(any())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));

        // Default mock behavior for roles: return empty so it creates/saves new default role
        when(roleRepository.findByRoleName(any(RoleName.class))).thenAnswer(inv -> {
            RoleName rn = inv.getArgument(0);
            Role r = new Role();
            r.setRoleName(rn);
            r.setStatus(GeneralStatus.ACTIVE);
            return Optional.of(r);
        });

        dataInitializer = new DataInitializer(roleRepository, userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Roles and default permissions are synchronized on startup")
    void testRolesAndPermissionsSynchronized() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEnabled", false);

        CommandLineRunner runner = dataInitializer.initDefaultData();
        runner.run();

        // All RoleName enum values should be synchronized
        for (RoleName roleName : RoleName.values()) {
            verify(roleRepository, atLeastOnce()).save(argThat(r ->
                    r.getRoleName() == roleName &&
                    r.getStatus() == GeneralStatus.ACTIVE &&
                    r.getPermissions() != null &&
                    !r.getPermissions().isBlank()
            ));
        }

        // When bootstrap is disabled, userRepository is not checked or modified for admin bootstrap
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("When Admin already exists, startup never resets password or creates duplicates")
    void testExistingAdminPasswordNeverReset() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminUsername", "admin");
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEmail", "admin@sms.com");
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminPassword", "NewSecretPass123");

        // Existing admin account exists
        when(userRepository.existsByRole_RoleName(RoleName.ADMIN)).thenReturn(true);

        CommandLineRunner runner = dataInitializer.initDefaultData();
        runner.run();

        // Never save or modify any User
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When bootstrap enabled and no Admin exists, creates initial admin with encoded password")
    void testBootstrapInitialAdminSuccess() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminUsername", "system_admin");
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEmail", "sysadmin@sms.vn");
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminPassword", "ComplexPassword987!");

        // No admin exists in database
        when(userRepository.existsByRole_RoleName(RoleName.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername("system_admin")).thenReturn(false);

        CommandLineRunner runner = dataInitializer.initDefaultData();
        runner.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());

        User created = captor.getValue();
        assertEquals("system_admin", created.getUsername());
        assertEquals("sysadmin@sms.vn", created.getEmail());
        assertEquals("encoded_ComplexPassword987!", created.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, created.getStatus());
        assertEquals(0, created.getFailedLoginAttempts());
        assertNull(created.getLockedUntil());
        assertEquals(RoleName.ADMIN, created.getRole().getRoleName());
    }

    @Test
    @DisplayName("When bootstrap enabled but password is blank, skips admin creation")
    void testBootstrapDisabledWhenPasswordBlank() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminUsername", "admin");
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminPassword", "");

        when(userRepository.existsByRole_RoleName(RoleName.ADMIN)).thenReturn(false);

        CommandLineRunner runner = dataInitializer.initDefaultData();
        runner.run();

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When bootstrap disabled (default), never creates admin even if no admin exists")
    void testBootstrapDisabledByDefault() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "bootstrapAdminEnabled", false);

        when(userRepository.existsByRole_RoleName(RoleName.ADMIN)).thenReturn(false);

        CommandLineRunner runner = dataInitializer.initDefaultData();
        runner.run();

        verify(userRepository, never()).save(any());
    }
}

