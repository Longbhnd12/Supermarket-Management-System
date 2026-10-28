package com.sms.backend.service;

import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.enums.AccountStatus;

import java.util.List;

/**
 * Service interface for User account operations.
 * TODO (SDD): Confirm password reset flow (reset code generation, expiry, verification).
 * TODO (SDD): Confirm failed login lockout policy thresholds.
 * TODO (SDD): Confirm whether user deletion is soft (status=INACTIVE) or hard.
 */
public interface UserService {

    List<UserResponse> getAllUsers();

    List<UserResponse> getUsersByStatus(AccountStatus status);

    UserResponse getUserById(Long id);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUserStatus(Long id, AccountStatus status);

    void deleteUser(Long id);
}
