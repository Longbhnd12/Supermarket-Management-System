package com.sms.backend.service;

import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UpdateUserRequest;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.serviceImpl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock EmployeeRepository employees;
    @Mock SupplierRepository suppliers;
    @Mock SystemLogService logs;
    @Mock CurrentUserProvider currentUser;

    UserServiceImpl service;
    User admin;

    @BeforeEach
    void setup() {
        service = new UserServiceImpl(users, roles, employees, suppliers, ENCODER, logs, currentUser);
        admin = new User();
        admin.setUserId(1L);
        admin.setUsername("admin");
        admin.setRole(role(10L, RoleName.ADMIN));
        lenient().when(currentUser.currentUser()).thenReturn(Optional.of(admin));
        lenient().when(currentUser.clientIp()).thenReturn("127.0.0.1");
        lenient().when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getUserId() == null) user.setUserId(50L);
            return user;
        });
    }

    private Role role(Long id, RoleName name) {
        Role role = new Role();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setStatus(GeneralStatus.ACTIVE);
        return role;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setFullName("Tran Thi B");
        employee.setStatus(GeneralStatus.ACTIVE);
        return employee;
    }

    private CreateUserRequest createRequest(Long roleId, Long employeeId, Long supplierId) {
        return new CreateUserRequest(roleId, employeeId, supplierId, " cashier02 ", "Cashier02@SMS.vn", "Secret123", null);
    }

    // ---------- UC05 create ----------

    @Test
    void createsCashierLinkedToEmployeeWithHashedPassword() {
        when(roles.findById(3L)).thenReturn(Optional.of(role(3L, RoleName.CASHIER)));
        when(employees.findById(20L)).thenReturn(Optional.of(employee(20L)));

        UserResponse result = service.createUser(createRequest(3L, 20L, null));

        assertEquals("cashier02", result.username());
        assertEquals("cashier02@sms.vn", result.email());
        assertEquals(AccountStatus.ACTIVE, result.status());
        assertEquals(20L, result.employeeId());
        verify(users).saveAndFlush(argThat((User user) -> ENCODER.matches("Secret123", user.getPasswordHash())));
        verify(logs).createLog(eq(admin), eq("USER_CREATED"), eq("USER"), eq(50L), anyString(), eq("127.0.0.1"));
    }

    @Test
    void staffAccountMustBeLinkedToEmployee() {
        when(roles.findById(3L)).thenReturn(Optional.of(role(3L, RoleName.CASHIER)));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createUser(createRequest(3L, null, null)));
        assertTrue(error.getMessage().contains("linked to an employee"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void adminAccountMayHaveNoEmployee() {
        when(roles.findById(10L)).thenReturn(Optional.of(role(10L, RoleName.ADMIN)));

        UserResponse result = service.createUser(createRequest(10L, null, null));
        assertEquals("ADMIN", result.roleName());
        assertNull(result.employeeId());
    }

    @Test
    void supplierAccountMustBeLinkedToSupplier() {
        when(roles.findById(5L)).thenReturn(Optional.of(role(5L, RoleName.SUPPLIER)));
        assertThrows(BadRequestException.class, () -> service.createUser(createRequest(5L, null, null)));

        Supplier supplier = new Supplier();
        supplier.setSupplierId(7L);
        supplier.setSupplierName("Vinamilk");
        when(suppliers.findById(7L)).thenReturn(Optional.of(supplier));
        UserResponse result = service.createUser(createRequest(5L, 99L, 7L));
        assertEquals(7L, result.supplierId());
        assertNull(result.employeeId(), "a Supplier account is never linked to an employee");
    }

    @Test
    void employeeAlreadyLinkedToAnotherAccountIsRejected() {
        when(roles.findById(3L)).thenReturn(Optional.of(role(3L, RoleName.CASHIER)));
        when(employees.findById(20L)).thenReturn(Optional.of(employee(20L)));
        User other = new User();
        other.setUserId(8L);
        other.setUsername("cashier01");
        when(users.findByEmployee_EmployeeId(20L)).thenReturn(Optional.of(other));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createUser(createRequest(3L, 20L, null)));
        assertTrue(error.getMessage().contains("cashier01"));
    }

    @Test
    void duplicateUsernameOrEmailIsRejected() {
        when(users.existsByUsername("cashier02")).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createUser(createRequest(3L, 20L, null)));
        assertEquals("Username/Email already exists", error.getMessage());
    }

    // ---------- UC05 update / status / delete ----------

    @Test
    void adminCannotDeactivateOwnAccount() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        admin.setEmail("admin@sms.vn");

        assertThrows(BadRequestException.class, () -> service.updateUserStatus(1L, AccountStatus.INACTIVE));
        assertThrows(BadRequestException.class, () -> service.deleteUser(1L));
        assertThrows(BadRequestException.class, () -> service.updateUser(1L,
                new UpdateUserRequest(10L, null, null, "admin", "admin@sms.vn", AccountStatus.INACTIVE)));
    }

    @Test
    void activatingLockedAccountUnlocksIt() {
        User locked = new User();
        locked.setUserId(9L);
        locked.setUsername("cashier09");
        locked.setEmail("c9@sms.vn");
        locked.setStatus(AccountStatus.LOCKED);
        locked.setFailedLoginAttempts(3);
        locked.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(users.findById(9L)).thenReturn(Optional.of(locked));
        when(users.save(locked)).thenReturn(locked);

        UserResponse result = service.updateUserStatus(9L, AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, result.status());
        assertEquals(0, locked.getFailedLoginAttempts());
        assertNull(locked.getLockedUntil());
    }

    @Test
    void lockedStatusCannotBeSetManually() {
        User user = new User();
        user.setUserId(9L);
        user.setEmail("c9@sms.vn");
        when(users.findById(9L)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> service.updateUserStatus(9L, AccountStatus.LOCKED));
    }

    @Test
    void deleteReleasesUsernameAndEmailButKeepsTheRow() {
        User user = new User();
        user.setUserId(9L);
        user.setUsername("cashier09");
        user.setEmail("c9@sms.vn");
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmployee(employee(20L));
        when(users.findById(9L)).thenReturn(Optional.of(user));

        service.deleteUser(9L);

        assertEquals("deleted_9", user.getUsername());
        assertTrue(user.getEmail().endsWith("@deleted.invalid"));
        assertEquals(AccountStatus.INACTIVE, user.getStatus());
        assertNull(user.getEmployee());
        verify(users).save(user);
        verify(users, never()).delete(any());
        verify(logs).createLog(eq(admin), eq("USER_DELETED"), eq("USER"), eq(9L), contains("cashier09"), anyString());
    }

    @Test
    void deletedAccountIsNotFound() {
        User deleted = new User();
        deleted.setUserId(9L);
        deleted.setEmail("deleted_9@deleted.invalid");
        when(users.findById(9L)).thenReturn(Optional.of(deleted));

        assertThrows(ResourceNotFoundException.class, () -> service.getUserById(9L));
    }
}
