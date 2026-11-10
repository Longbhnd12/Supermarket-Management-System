package com.sms.backend.service;

import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UpdateUserRequest;
import com.sms.backend.dto.user.UserFormOptionsResponse;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.enums.SupplierStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.serviceImpl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceRolePositionPolicyTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private EmployeeRepository employeeRepository;
    private SupplierRepository supplierRepository;
    private SystemLogService systemLogService;
    private CurrentUserProvider currentUserProvider;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    private final Map<Long, Role> rolesMap = new HashMap<>();
    private final Map<Long, Employee> employeesMap = new HashMap<>();
    private final Map<Long, Supplier> suppliersMap = new HashMap<>();
    private final Map<Long, User> usersMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        rolesMap.clear();
        employeesMap.clear();
        suppliersMap.clear();
        usersMap.clear();

        roleRepository = createProxy(RoleRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(rolesMap.get(args[0]));
            }
            if ("findAll".equals(method.getName())) {
                return new ArrayList<>(rolesMap.values());
            }
            return defaultValue(method);
        });

        employeeRepository = createProxy(EmployeeRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(employeesMap.get(args[0]));
            }
            if ("findByStatus".equals(method.getName())) {
                GeneralStatus st = (GeneralStatus) args[0];
                return employeesMap.values().stream().filter(e -> e.getStatus() == st).toList();
            }
            return defaultValue(method);
        });

        supplierRepository = createProxy(SupplierRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(suppliersMap.get(args[0]));
            }
            if ("findByStatus".equals(method.getName())) {
                SupplierStatus st = (SupplierStatus) args[0];
                return suppliersMap.values().stream().filter(s -> s.getStatus() == st).toList();
            }
            return defaultValue(method);
        });

        userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(usersMap.get(args[0]));
            }
            if ("findByEmployee_EmployeeId".equals(method.getName())) {
                Long empId = (Long) args[0];
                return usersMap.values().stream()
                        .filter(u -> u.getEmployee() != null && empId.equals(u.getEmployee().getEmployeeId()))
                        .findFirst();
            }
            if ("findBySupplier_SupplierId".equals(method.getName())) {
                Long supId = (Long) args[0];
                return usersMap.values().stream()
                        .filter(u -> u.getSupplier() != null && supId.equals(u.getSupplier().getSupplierId()))
                        .findFirst();
            }
            if ("findLinkedEmployeeIds".equals(method.getName())) {
                return usersMap.values().stream()
                        .filter(u -> u.getEmployee() != null)
                        .map(u -> u.getEmployee().getEmployeeId())
                        .toList();
            }
            if ("findLinkedSupplierIds".equals(method.getName())) {
                return usersMap.values().stream()
                        .filter(u -> u.getSupplier() != null)
                        .map(u -> u.getSupplier().getSupplierId())
                        .toList();
            }
            if ("existsByUsername".equals(method.getName()) || "existsByEmail".equals(method.getName())) {
                return false;
            }
            if ("saveAndFlush".equals(method.getName()) || "save".equals(method.getName())) {
                User u = (User) args[0];
                if (u.getUserId() == null) u.setUserId(999L);
                usersMap.put(u.getUserId(), u);
                return u;
            }
            return defaultValue(method);
        });

        systemLogService = createProxy(SystemLogService.class, (proxy, method, args) -> null);
        currentUserProvider = new CurrentUserProvider(userRepository) {
            @Override
            public Optional<User> currentUser() {
                return Optional.empty();
            }

            @Override
            public String clientIp() {
                return "127.0.0.1";
            }
        };
        emailService = createProxy(EmailService.class, (proxy, method, args) -> null);
        passwordEncoder = createProxy(PasswordEncoder.class, (proxy, method, args) -> {
            if ("encode".equals(method.getName())) return "encoded-" + args[0];
            return defaultValue(method);
        });

        userService = new UserServiceImpl(
                userRepository,
                roleRepository,
                employeeRepository,
                supplierRepository,
                passwordEncoder,
                systemLogService,
                currentUserProvider,
                emailService
        );

        // Prepopulate roles
        rolesMap.put(1L, makeRole(1L, RoleName.ADMIN));
        rolesMap.put(2L, makeRole(2L, RoleName.MANAGER));
        rolesMap.put(3L, makeRole(3L, RoleName.CASHIER));
        rolesMap.put(4L, makeRole(4L, RoleName.WAREHOUSE_STAFF));
        rolesMap.put(5L, makeRole(5L, RoleName.SUPPLIER));
    }

    private Role makeRole(Long id, RoleName name) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName(name);
        r.setStatus(GeneralStatus.ACTIVE);
        return r;
    }

    private Employee makeEmployee(Long id, String name, String position) {
        Employee e = new Employee();
        e.setEmployeeId(id);
        e.setEmployeeCode("EMP-" + id);
        e.setFullName(name);
        e.setPosition(position);
        e.setStatus(GeneralStatus.ACTIVE);
        return e;
    }

    private Supplier makeSupplier(Long id, String name) {
        Supplier s = new Supplier();
        s.setSupplierId(id);
        s.setSupplierCode("SUP-" + id);
        s.setSupplierName(name);
        s.setStatus(SupplierStatus.ACTIVE);
        return s;
    }

    @Test
    @DisplayName("CASHIER role rejects Security Guard employee")
    void cashierRejectsSecurityGuard() {
        employeesMap.put(101L, makeEmployee(101L, "Bao Ve A", "Security Guard"));

        CreateUserRequest req = new CreateUserRequest(3L, 101L, null, "cashier_sec", "sec@sms.vn", "Secret123", AccountStatus.ACTIVE);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.createUser(req));

        assertTrue(ex.getMessage().contains("Role CASHIER requires an employee with position 'Cashier'"));
        assertTrue(ex.getMessage().contains("Security Guard"));
    }

    @Test
    @DisplayName("CASHIER role rejects Cleaner, Stock Handler, and Accounting")
    void cashierRejectsOtherUnrelatedPositions() {
        employeesMap.put(102L, makeEmployee(102L, "Lao Cong B", "Cleaner"));
        employeesMap.put(103L, makeEmployee(103L, "Boc Xep C", "Stock Handler"));
        employeesMap.put(104L, makeEmployee(104L, "Ke Toan D", "Accounting"));

        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(3L, 102L, null, "c1", "c1@sms.vn", "Secret123", AccountStatus.ACTIVE)));
        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(3L, 103L, null, "c2", "c2@sms.vn", "Secret123", AccountStatus.ACTIVE)));
        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(3L, 104L, null, "c3", "c3@sms.vn", "Secret123", AccountStatus.ACTIVE)));
    }

    @Test
    @DisplayName("CASHIER role accepts Cashier employee")
    void cashierAcceptsCashierEmployee() {
        employeesMap.put(105L, makeEmployee(105L, "Thu Ngan E", "Cashier"));

        CreateUserRequest req = new CreateUserRequest(3L, 105L, null, "cashier_ok", "ok@sms.vn", "Secret123", AccountStatus.ACTIVE);
        UserResponse resp = userService.createUser(req);

        assertNotNull(resp);
        assertEquals(105L, resp.employeeId());
        assertEquals("Thu Ngan E", resp.employeeFullName());
    }

    @Test
    @DisplayName("MANAGER role accepts Manager position and rejects Cashier")
    void managerPolicy() {
        employeesMap.put(201L, makeEmployee(201L, "Cashier Not Mgr", "Cashier"));
        employeesMap.put(202L, makeEmployee(202L, "Real Mgr", "Manager"));

        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(2L, 201L, null, "mgr_fail", "mf@sms.vn", "Secret123", AccountStatus.ACTIVE)));

        UserResponse ok = userService.createUser(new CreateUserRequest(2L, 202L, null, "mgr_ok", "mo@sms.vn", "Secret123", AccountStatus.ACTIVE));
        assertEquals(202L, ok.employeeId());
    }

    @Test
    @DisplayName("WAREHOUSE_STAFF role accepts Inventory Staff and rejects Shelf Stocker")
    void warehouseStaffPolicy() {
        employeesMap.put(301L, makeEmployee(301L, "Shelf Stocker A", "Shelf Stocker"));
        employeesMap.put(302L, makeEmployee(302L, "Kho B", "Inventory Staff"));

        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(4L, 301L, null, "wh_fail", "wf@sms.vn", "Secret123", AccountStatus.ACTIVE)));

        UserResponse ok = userService.createUser(new CreateUserRequest(4L, 302L, null, "wh_ok", "wo@sms.vn", "Secret123", AccountStatus.ACTIVE));
        assertEquals(302L, ok.employeeId());
    }

    @Test
    @DisplayName("ADMIN role accepts Administrator employee or no employee, rejects Cashier")
    void adminPolicy() {
        employeesMap.put(401L, makeEmployee(401L, "Admin Thu Ngan", "Cashier"));
        employeesMap.put(402L, makeEmployee(402L, "Admin Sys", "Administrator"));

        // Admin without employee is valid
        UserResponse noEmp = userService.createUser(new CreateUserRequest(1L, null, null, "admin_no_emp", "an@sms.vn", "Secret123", AccountStatus.ACTIVE));
        assertNull(noEmp.employeeId());

        // Admin with Cashier employee is rejected
        assertThrows(BadRequestException.class, () ->
                userService.createUser(new CreateUserRequest(1L, 401L, null, "admin_fail", "af@sms.vn", "Secret123", AccountStatus.ACTIVE)));

        // Admin with Administrator employee is accepted
        UserResponse withAdminEmp = userService.createUser(new CreateUserRequest(1L, 402L, null, "admin_ok", "ao@sms.vn", "Secret123", AccountStatus.ACTIVE));
        assertEquals(402L, withAdminEmp.employeeId());
    }

    @Test
    @DisplayName("SUPPLIER role links to supplier and ignores/clears employee")
    void supplierPolicy() {
        suppliersMap.put(501L, makeSupplier(501L, "Supplier Vinamilk"));

        CreateUserRequest req = new CreateUserRequest(5L, 999L, 501L, "supplier_user", "sup@sms.vn", "Secret123", AccountStatus.ACTIVE);
        UserResponse resp = userService.createUser(req);

        assertEquals(501L, resp.supplierId());
        assertNull(resp.employeeId());
    }

    @Test
    @DisplayName("Updating user with incompatible position is rejected")
    void updateUserRejectsIncompatiblePosition() {
        Employee cashierEmp = makeEmployee(501L, "Cashier 1", "Cashier");
        Employee guardEmp = makeEmployee(502L, "Guard 1", "Security Guard");
        employeesMap.put(501L, cashierEmp);
        employeesMap.put(502L, guardEmp);

        User existing = new User();
        existing.setUserId(10L);
        existing.setUsername("cashier10");
        existing.setEmail("c10@sms.vn");
        existing.setRole(rolesMap.get(3L));
        existing.setEmployee(cashierEmp);
        existing.setStatus(AccountStatus.ACTIVE);
        usersMap.put(10L, existing);

        UpdateUserRequest updateReq = new UpdateUserRequest(3L, 502L, null, "cashier10", "c10@sms.vn", AccountStatus.ACTIVE);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.updateUser(10L, updateReq));

        assertTrue(ex.getMessage().contains("Role CASHIER requires an employee with position 'Cashier'"));
        assertTrue(ex.getMessage().contains("Security Guard"));
    }

    @Test
    @DisplayName("getFormOptions filters employees when roleId is provided")
    void getFormOptionsWithRoleId() {
        employeesMap.put(1L, makeEmployee(1L, "Cashier 1", "Cashier"));
        employeesMap.put(2L, makeEmployee(2L, "Guard 1", "Security Guard"));
        employeesMap.put(3L, makeEmployee(3L, "Mgr 1", "Manager"));

        UserFormOptionsResponse cashierOptions = userService.getFormOptions(null, 3L);
        assertEquals(1, cashierOptions.employees().size());
        assertEquals("Cashier", cashierOptions.employees().get(0).position());

        UserFormOptionsResponse allOptions = userService.getFormOptions(null);
        assertEquals(3, allOptions.employees().size());
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Optional.class)) return Optional.empty();
        if (returnType.equals(List.class)) return Collections.emptyList();
        if (returnType.equals(Set.class)) return Collections.emptySet();
        if (returnType.equals(boolean.class)) return false;
        if (returnType.equals(int.class)) return 0;
        if (returnType.equals(long.class)) return 0L;
        return null;
    }
}
