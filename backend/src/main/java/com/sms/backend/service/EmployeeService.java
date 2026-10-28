package com.sms.backend.service;

import com.sms.backend.dto.employee.EmployeeRequest;
import com.sms.backend.dto.employee.EmployeeResponse;
import com.sms.backend.enums.GeneralStatus;

import java.util.List;

/**
 * Service interface for Employee operations.
 * TODO (SDD): Confirm whether an employee linked to an active User account can be deactivated.
 */
public interface EmployeeService {

    List<EmployeeResponse> getAllEmployees();

    List<EmployeeResponse> getEmployeesByStatus(GeneralStatus status);

    EmployeeResponse getEmployeeById(Long id);

    EmployeeResponse createEmployee(EmployeeRequest request);

    EmployeeResponse updateEmployee(Long id, EmployeeRequest request);

    void deleteEmployee(Long id);
}
