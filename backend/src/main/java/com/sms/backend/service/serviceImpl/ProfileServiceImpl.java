package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.profile.ProfileResponse;
import com.sms.backend.dto.profile.UpdateProfileRequest;
import com.sms.backend.entity.Employee;
import com.sms.backend.entity.User;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.UnauthorizedException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.ProfileService;
import com.sms.backend.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * UC44 - View/Update Profile.
 * <ul>
 *     <li>BR-01: Role and Status are read-only here (only Admin changes them in UC05).</li>
 *     <li>BR-02: the email must not be used by another account (AT1 "This email is already in use").</li>
 *     <li>BR-03: accounts without a linked employee only edit their Email.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    static final String MSG_EMAIL_IN_USE = "This email is already in use";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemLogService systemLogService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        return toResponse(findUser(username));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = findUser(username);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailAndUserIdNot(email, user.getUserId())) {
            throw new BadRequestException(MSG_EMAIL_IN_USE);
        }

        String before = describe(user);
        Employee employee = user.getEmployee();
        if (employee != null) {
            String fullName = request.fullName() == null ? "" : request.fullName().trim();
            String phone = request.phone() == null ? "" : request.phone().trim();
            if (fullName.isEmpty()) {
                throw new BadRequestException("The Full Name field is required.");
            }
            if (phone.isEmpty()) {
                // employee.phone is NOT NULL in the database
                throw new BadRequestException("The Phone field is required.");
            }
            employee.setFullName(fullName);
            employee.setPhone(phone);
            employee.setUpdatedAt(LocalDateTime.now());
            employeeRepository.save(employee);
        }

        user.setEmail(email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException(MSG_EMAIL_IN_USE);
        }

        systemLogService.createLog(user, "PROFILE_UPDATED", "USER", user.getUserId(),
                "Profile updated: " + before + " -> " + describe(user), currentUserProvider.clientIp());
        return toResponse(user);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user was not found"));
    }

    private String describe(User user) {
        Employee employee = user.getEmployee();
        return "{email=" + user.getEmail()
                + (employee == null ? "" : ", fullName=" + employee.getFullName() + ", phone=" + employee.getPhone())
                + "}";
    }

    private ProfileResponse toResponse(User user) {
        Employee employee = user.getEmployee();
        return new ProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole() == null || user.getRole().getRoleName() == null ? "" : user.getRole().getRoleName().name(),
                user.getStatus(),
                employee != null,
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                employee == null ? null : employee.getPhone(),
                employee == null ? null : employee.getPosition()
        );
    }
}
