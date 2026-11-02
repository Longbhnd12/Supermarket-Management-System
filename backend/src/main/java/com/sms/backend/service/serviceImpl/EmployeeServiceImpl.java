package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.employee.EmployeeRequest;
import com.sms.backend.dto.employee.EmployeeResponse;
import com.sms.backend.entity.Employee;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.EmployeeRepository;
import com.sms.backend.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Override
    public List<EmployeeResponse> getAllEmployees() {
        return employeeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<EmployeeResponse> getEmployeesByStatus(GeneralStatus status) {
        return employeeRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public EmployeeResponse getEmployeeById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        // TODO: Implement – validate code uniqueness, create Employee
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        // TODO: Implement – update Employee fields
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        // TODO (SDD): Confirm deletion policy – guard against linked active User account
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    private EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(
                e.getEmployeeId(),
                e.getEmployeeCode(),
                e.getFullName(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getPhone(),
                e.getEmail(),
                e.getAddress(),
                e.getPosition(),
                e.getHireDate(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
