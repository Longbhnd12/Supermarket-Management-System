package com.sms.backend.service;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UpdateUserRequest;
import com.sms.backend.dto.user.UserFormOptionsResponse;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.enums.AccountStatus;

import java.util.List;

/**
 * UC04 - View Users, UC05 - Manage Users.
 */
public interface UserService {

    /** Plain list (used by the POS screen to load cashiers). */
    List<UserResponse> getAllUsers();

    List<UserResponse> getUsersByStatus(AccountStatus status);

    /** UC04: search by username/email, filter by role and status, paginated. */
    PageResponse<UserResponse> searchUsers(String search, Long roleId, AccountStatus status, int page, int size);

    UserResponse getUserById(Long id);

    /** UC05: roles, free employees and free suppliers for the Create/Edit User form. */
    UserFormOptionsResponse getFormOptions(Long editingUserId);

    UserFormOptionsResponse getFormOptions(Long editingUserId, Long roleId);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    /** UC05 AT2: activate / deactivate (activating also unlocks a locked account). */
    UserResponse updateUserStatus(Long id, AccountStatus status);

    /** UC05 AT3: remove from active use, keep history, release username/email. */
    void deleteUser(Long id);
}
