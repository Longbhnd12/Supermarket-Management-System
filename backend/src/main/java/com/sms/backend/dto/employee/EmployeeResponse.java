package com.sms.backend.dto.employee;

import com.sms.backend.enums.Gender;
import com.sms.backend.enums.GeneralStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for Employee.
 */
public record EmployeeResponse(
        Long employeeId,
        String employeeCode,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String email,
        String address,
        String position,
        LocalDate hireDate,
        GeneralStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
