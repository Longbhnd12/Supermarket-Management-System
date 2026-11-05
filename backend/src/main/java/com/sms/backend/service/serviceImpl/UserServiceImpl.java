package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UpdateUserRequest;
import com.sms.backend.dto.user.UserFormOptionsResponse;
import com.sms.backend.dto.user.UserFormOptionsResponse.EmployeeOption;
import com.sms.backend.dto.user.UserFormOptionsResponse.RoleOption;
import com.sms.backend.dto.user.UserFormOptionsResponse.SupplierOption;
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
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.SupplierRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.SystemLogService;
import com.sms.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UC04 - View Users, UC05 - Manage Users.
 * <ul>
 *     <li>BR-01: Manager / Cashier / Inventory Staff must be linked to an employee; Admin may not be.
 *     Supplier accounts must be linked to a supplier.</li>
 *     <li>BR-02: username and email are unique.</li>
 *     <li>BR-03: a deleted account releases its username / email.</li>
 *     <li>BR-04: an Admin cannot deactivate, delete or change the role of their own account.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    /** Deleted accounts get an email on this (reserved, never deliverable) domain. */
    static final String DELETED_EMAIL_DOMAIN = "@deleted.invalid";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ENTITY_TYPE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final SupplierRepository supplierRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemLogService systemLogService;
    private final CurrentUserProvider currentUserProvider;

    // ---------------- UC04 - View Users ----------------

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !isDeleted(user))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<UserResponse> getUsersByStatus(AccountStatus status) {
        return userRepository.findByStatus(status).stream()
                .filter(user -> !isDeleted(user))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PageResponse<UserResponse> searchUsers(String search, Long roleId, AccountStatus status, int page, int size) {
        String keyword = search == null || search.isBlank()
                ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("username").ascending());

        Page<UserResponse> result = userRepository.search(keyword, roleId, status, pageable).map(this::toResponse);
        return PageResponse.from(result);
    }

    @Override
    public UserResponse getUserById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public UserFormOptionsResponse getFormOptions(Long editingUserId) {
        User editing = editingUserId == null ? null : userRepository.findById(editingUserId).orElse(null);
        Long ownEmployeeId = editing != null && editing.getEmployee() != null ? editing.getEmployee().getEmployeeId() : null;
        Long ownSupplierId = editing != null && editing.getSupplier() != null ? editing.getSupplier().getSupplierId() : null;

        List<RoleOption> roles = roleRepository.findAll().stream()
                .filter(role -> role.getRoleName() != null && role.getStatus() != GeneralStatus.INACTIVE)
                .sorted(Comparator.comparing(role -> role.getRoleName().ordinal()))
                .map(role -> new RoleOption(role.getRoleId(), role.getRoleName().name()))
                .toList();

        Set<Long> linkedEmployees = new HashSet<>(userRepository.findLinkedEmployeeIds());
        List<EmployeeOption> employees = employeeRepository.findByStatus(GeneralStatus.ACTIVE).stream()
                .filter(employee -> !linkedEmployees.contains(employee.getEmployeeId())
                        || Objects.equals(employee.getEmployeeId(), ownEmployeeId))
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(employee -> new EmployeeOption(employee.getEmployeeId(), employee.getEmployeeCode(),
                        employee.getFullName(), employee.getPosition()))
                .toList();

        Set<Long> linkedSuppliers = new HashSet<>(userRepository.findLinkedSupplierIds());
        List<SupplierOption> suppliers = supplierRepository.findByStatus(SupplierStatus.ACTIVE).stream()
                .filter(supplier -> !linkedSuppliers.contains(supplier.getSupplierId())
                        || Objects.equals(supplier.getSupplierId(), ownSupplierId))
                .sorted(Comparator.comparing(Supplier::getSupplierName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(supplier -> new SupplierOption(supplier.getSupplierId(), supplier.getSupplierCode(),
                        supplier.getSupplierName()))
                .toList();

        return new UserFormOptionsResponse(roles, employees, suppliers);
    }

    // ---------------- UC05 - Manage Users ----------------

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureUnique(username, email, null);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(toEditableStatus(request.status() == null ? AccountStatus.ACTIVE : request.status()));
        user.setFailedLoginAttempts(0);
        applyRoleAndLinks(user, request.roleId(), request.employeeId(), request.supplierId());

        User saved = saveUnique(user);
        log("USER_CREATED", saved, "Created account " + saved.getUsername() + " (role " + roleName(saved) + ")");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findById(id);
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        ensureUnique(username, email, id);

        String before = describe(user);
        if (isCurrentUser(user)) {
            // BR-04: an Admin cannot lock themselves out
            if (request.status() != AccountStatus.ACTIVE) {
                throw new BadRequestException("You cannot deactivate your own account");
            }
            if (user.getRole() != null && !Objects.equals(user.getRole().getRoleId(), request.roleId())) {
                throw new BadRequestException("You cannot change the role of your own account");
            }
        }

        user.setUsername(username);
        user.setEmail(email);
        applyStatus(user, toEditableStatus(request.status()));
        applyRoleAndLinks(user, request.roleId(), request.employeeId(), request.supplierId());

        User saved = saveUnique(user);
        log("USER_UPDATED", saved, "Updated account: " + before + " -> " + describe(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, AccountStatus status) {
        User user = findById(id);
        AccountStatus target = toEditableStatus(status);
        if (target != AccountStatus.ACTIVE && isCurrentUser(user)) {
            throw new BadRequestException("You cannot deactivate your own account");
        }

        AccountStatus before = user.getStatus();
        applyStatus(user, target);
        User saved = userRepository.save(user);
        log("USER_STATUS_CHANGED", saved, "Status of " + saved.getUsername() + ": " + before + " -> " + saved.getStatus());
        return toResponse(saved);
    }

    /**
     * UC05 AT3 - Delete. The row is kept so past sales, stock movements and logs still point to it
     * (they reference user_id), but the account can never log in again and its username/email are
     * released for reuse (BR-03).
     */
    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);
        if (isCurrentUser(user)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        String original = user.getUsername() + " <" + user.getEmail() + ">";
        user.setUsername("deleted_" + user.getUserId());
        user.setEmail("deleted_" + user.getUserId() + DELETED_EMAIL_DOMAIN);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(AccountStatus.INACTIVE);
        user.setEmployee(null);
        user.setSupplier(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);

        log("USER_DELETED", user, "Deleted account " + original);
    }

    // ---------------- helpers ----------------

    private void applyRoleAndLinks(User user, Long roleId, Long employeeId, Long supplierId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BadRequestException("Role not found"));
        if (role.getStatus() == GeneralStatus.INACTIVE) {
            throw new BadRequestException("Role " + role.getRoleName() + " is inactive");
        }
        RoleName roleName = role.getRoleName();

        Employee employee = null;
        Supplier supplier = null;
        if (roleName == RoleName.SUPPLIER) {
            if (supplierId == null) {
                throw new BadRequestException("A Supplier account must be linked to a supplier");
            }
            supplier = supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new BadRequestException("Supplier not found"));
            userRepository.findBySupplier_SupplierId(supplierId)
                    .filter(other -> !Objects.equals(other.getUserId(), user.getUserId()))
                    .ifPresent(other -> {
                        throw new BadRequestException("This supplier is already linked to account " + other.getUsername());
                    });
        } else {
            if (employeeId == null && roleName != RoleName.ADMIN) {
                // BR-01
                throw new BadRequestException("Manager, Cashier and Inventory Staff accounts must be linked to an employee");
            }
            if (employeeId != null) {
                employee = employeeRepository.findById(employeeId)
                        .orElseThrow(() -> new BadRequestException("Employee not found"));
                if (employee.getStatus() == GeneralStatus.INACTIVE
                        && (user.getEmployee() == null || !Objects.equals(user.getEmployee().getEmployeeId(), employeeId))) {
                    throw new BadRequestException("Employee " + employee.getFullName() + " is inactive");
                }
                userRepository.findByEmployee_EmployeeId(employeeId)
                        .filter(other -> !Objects.equals(other.getUserId(), user.getUserId()))
                        .ifPresent(other -> {
                            throw new BadRequestException("This employee is already linked to account " + other.getUsername());
                        });
            }
        }

        user.setRole(role);
        user.setEmployee(employee);
        user.setSupplier(supplier);
    }

    private void ensureUnique(String username, String email, Long excludeUserId) {
        boolean usernameTaken = excludeUserId == null
                ? userRepository.existsByUsername(username)
                : userRepository.existsByUsernameAndUserIdNot(username, excludeUserId);
        boolean emailTaken = excludeUserId == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndUserIdNot(email, excludeUserId);
        if (usernameTaken || emailTaken) {
            throw new BadRequestException("Username/Email already exists"); // UC05 AT1
        }
    }

    private User saveUnique(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Username/Email already exists");
        }
    }

    /** Only ACTIVE / INACTIVE can be chosen by an Admin; LOCKED is set by the login lockout. */
    private AccountStatus toEditableStatus(AccountStatus status) {
        if (status != AccountStatus.ACTIVE && status != AccountStatus.INACTIVE) {
            throw new BadRequestException("Status must be ACTIVE or INACTIVE");
        }
        return status;
    }

    private void applyStatus(User user, AccountStatus status) {
        if (status == AccountStatus.ACTIVE && user.getStatus() != AccountStatus.ACTIVE) {
            // Activating also unlocks an account locked by too many failed logins
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user.setStatus(status);
    }

    private boolean isCurrentUser(User user) {
        return currentUserProvider.currentUser()
                .map(current -> Objects.equals(current.getUserId(), user.getUserId()))
                .orElse(false);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .filter(user -> !isDeleted(user))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private boolean isDeleted(User user) {
        return user.getEmail() != null && user.getEmail().endsWith(DELETED_EMAIL_DOMAIN);
    }

    private String roleName(User user) {
        return user.getRole() == null || user.getRole().getRoleName() == null ? "" : user.getRole().getRoleName().name();
    }

    private String describe(User user) {
        return "{username=" + user.getUsername() + ", email=" + user.getEmail() + ", role=" + roleName(user)
                + ", status=" + user.getStatus()
                + ", employeeId=" + (user.getEmployee() == null ? null : user.getEmployee().getEmployeeId())
                + ", supplierId=" + (user.getSupplier() == null ? null : user.getSupplier().getSupplierId()) + "}";
    }

    private void log(String action, User target, String description) {
        systemLogService.createLog(
                currentUserProvider.currentUser().orElse(null),
                action,
                ENTITY_TYPE,
                target.getUserId(),
                description,
                currentUserProvider.clientIp());
    }

    private UserResponse toResponse(User u) {
        Long employeeId = u.getEmployee() != null ? u.getEmployee().getEmployeeId() : null;
        String employeeFullName = u.getEmployee() != null ? u.getEmployee().getFullName() : null;
        Long supplierId = u.getSupplier() != null ? u.getSupplier().getSupplierId() : null;
        String supplierName = u.getSupplier() != null ? u.getSupplier().getSupplierName() : null;

        return new UserResponse(
                u.getUserId(),
                u.getRole() != null ? u.getRole().getRoleId() : null,
                u.getRole() != null && u.getRole().getRoleName() != null ? u.getRole().getRoleName().name() : null,
                employeeId,
                employeeFullName,
                supplierId,
                supplierName,
                u.getUsername(),
                u.getEmail(),
                u.getStatus(),
                u.getLastLoginAt(),
                u.getLockedUntil()
        );
    }
}
