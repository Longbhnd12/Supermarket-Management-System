package com.sms.backend.service;

import com.sms.backend.dto.profile.ProfileResponse;
import com.sms.backend.dto.profile.UpdateProfileRequest;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.serviceImpl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock UserRepository users;
    @Mock EmployeeRepository employees;
    @Mock SystemLogService logs;
    @Mock CurrentUserProvider currentUser;

    ProfileServiceImpl service;

    @BeforeEach
    void setup() {
        service = new ProfileServiceImpl(users, employees, logs, currentUser);
    }

    private User user(boolean withEmployee) {
        Role role = new Role();
        role.setRoleName(withEmployee ? RoleName.CASHIER : RoleName.ADMIN);
        User user = new User();
        user.setUserId(7L);
        user.setUsername("cashier01");
        user.setEmail("cashier01@sms.vn");
        user.setStatus(AccountStatus.ACTIVE);
        user.setRole(role);
        if (withEmployee) {
            Employee employee = new Employee();
            employee.setEmployeeId(20L);
            employee.setEmployeeCode("EMP020");
            employee.setFullName("Tran Thi B");
            employee.setPhone("0901000902");
            user.setEmployee(employee);
        }
        return user;
    }

    @Test
    void profileShowsEmployeeFieldsAndReadOnlyRole() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user(true)));

        ProfileResponse profile = service.getProfile("cashier01");

        assertTrue(profile.hasEmployee());
        assertEquals("Tran Thi B", profile.fullName());
        assertEquals("CASHIER", profile.role());
    }

    @Test
    void updatesEmployeeNamePhoneAndAccountEmail() {
        User user = user(true);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        ProfileResponse result = service.updateProfile("cashier01",
                new UpdateProfileRequest(" Tran Thi Bich ", "0912345678", "New.Mail@SMS.vn"));

        assertEquals("Tran Thi Bich", result.fullName());
        assertEquals("0912345678", result.phone());
        assertEquals("new.mail@sms.vn", result.email());
        verify(employees).save(user.getEmployee());
        verify(users).saveAndFlush(user);
        verify(logs).createLog(eq(user), eq("PROFILE_UPDATED"), eq("USER"), eq(7L), anyString(), any());
    }

    @Test
    void accountWithoutEmployeeOnlyChangesEmail() {
        User user = user(false);
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user));

        ProfileResponse result = service.updateProfile("cashier01",
                new UpdateProfileRequest("Ignored Name", "0999999999", "admin2@sms.vn"));

        assertFalse(result.hasEmployee());
        assertNull(result.fullName());
        assertEquals("admin2@sms.vn", result.email());
        verify(employees, never()).save(any());
    }

    @Test
    void emailUsedByAnotherAccountIsRejected() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user(true)));
        when(users.existsByEmailAndUserIdNot("taken@sms.vn", 7L)).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.updateProfile("cashier01",
                new UpdateProfileRequest("Tran Thi B", "0901000902", "taken@sms.vn")));
        assertEquals("This email is already in use", error.getMessage());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void employeeNameIsRequired() {
        when(users.findByUsername("cashier01")).thenReturn(Optional.of(user(true)));

        assertThrows(BadRequestException.class, () -> service.updateProfile("cashier01",
                new UpdateProfileRequest("  ", "0901000902", "cashier01@sms.vn")));
    }
}
