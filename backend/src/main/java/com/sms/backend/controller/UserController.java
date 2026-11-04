package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.user.CreateUserRequest;
import com.sms.backend.dto.user.UpdateUserRequest;
import com.sms.backend.dto.user.UserFormOptionsResponse;
import com.sms.backend.dto.user.UserResponse;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UC04 - View Users, UC05 - Manage Users.
 * Access (SecurityConfig): GET /users is open to logged-in users (POS loads cashiers);
 * other GET endpoints need USER_VIEW, changes need USER_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Plain list kept for the POS screen. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestParam(required = false) AccountStatus status) {
        List<UserResponse> result = status != null
                ? userService.getUsersByStatus(status)
                : userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", result));
    }

    /** UC04: search + role/status filters + pagination. */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved",
                userService.searchUsers(search, roleId, status, page, size)));
    }

    /** UC05: dropdown data for the Create/Edit User screen. */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<UserFormOptionsResponse>> getFormOptions(
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Options retrieved", userService.getFormOptions(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved", userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User saved", userService.createUser(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User saved", userService.updateUser(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam AccountStatus status) {
        return ResponseEntity.ok(ApiResponse.success("User status updated", userService.updateUserStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted successfully.", null));
    }
}
