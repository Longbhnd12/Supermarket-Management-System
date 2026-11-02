package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<UserResponse> getUsersByStatus(AccountStatus status) {
        return userRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // TODO: Implement – hash password, validate unique username/email,
        //       resolve role/employee/supplier references
        // TODO (SDD): Confirm password hashing algorithm (BCrypt assumed)
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, AccountStatus status) {
        // TODO: Implement – update user status, unlock if ACTIVE
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        // TODO (SDD): Confirm deletion policy (soft vs hard)
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserResponse toResponse(User u) {
        Long employeeId = u.getEmployee() != null ? u.getEmployee().getEmployeeId() : null;
        String employeeFullName = u.getEmployee() != null ? u.getEmployee().getFullName() : null;
        Long supplierId = u.getSupplier() != null ? u.getSupplier().getSupplierId() : null;
        String supplierName = u.getSupplier() != null ? u.getSupplier().getSupplierName() : null;

        return new UserResponse(
                u.getUserId(),
                u.getRole().getRoleId(),
                u.getRole().getRoleName() != null ? u.getRole().getRoleName().name() : null,
                employeeId,
                employeeFullName,
                supplierId,
                supplierName,
                u.getUsername(),
                u.getEmail(),
                u.getStatus(),
                u.getLastLoginAt()
        );
    }
}
