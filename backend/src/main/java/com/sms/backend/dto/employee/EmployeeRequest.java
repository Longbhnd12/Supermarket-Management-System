package com.sms.backend.dto.employee;

import com.sms.backend.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating or updating an Employee.
 * TODO (SDD): Confirm whether employeeCode is auto-generated or user-supplied.
 */
public record EmployeeRequest(

        @NotBlank(message = "Employee code is required")
        @Size(max = 30, message = "Employee code must not exceed 30 characters")
        String employeeCode,

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        LocalDate dateOfBirth,

        Gender gender,

        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @NotBlank(message = "Email is required")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,

        @NotBlank(message = "Position is required")
        @Size(max = 100, message = "Position must not exceed 100 characters")
        String position,

        @NotNull(message = "Hire date is required")
        LocalDate hireDate
) {
}
