package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.employee.EmployeeRequest;
import com.sms.backend.dto.employee.EmployeeResponse;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeResponse>>> getAllEmployees(
            @RequestParam(required = false) GeneralStatus status) {
        List<EmployeeResponse> result = status != null
                ? employeeService.getEmployeesByStatus(status)
                : employeeService.getAllEmployees();
        return ResponseEntity.ok(ApiResponse.success("Employees retrieved", result));
    }

    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctPositions() {
        return ResponseEntity.ok(ApiResponse.success("Positions retrieved", employeeService.getDistinctPositions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Employee retrieved", employeeService.getEmployeeById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee created", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Employee updated", employeeService.updateEmployee(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployeeStatus(
            @PathVariable Long id,
            @RequestParam(required = false) GeneralStatus status,
            @RequestBody(required = false) Map<String, String> body) {
        GeneralStatus newStatus = status;
        if (newStatus == null && body != null && body.containsKey("status")) {
            try {
                newStatus = GeneralStatus.valueOf(body.get("status").trim().toUpperCase());
            } catch (Exception e) {
                throw new BadRequestException("Trạng thái không hợp lệ: " + body.get("status"));
            }
        }
        if (newStatus == null) {
            throw new BadRequestException("Thiếu thông tin trạng thái cần cập nhật (status)");
        }
        EmployeeResponse response = employeeService.updateStatus(id, newStatus);
        return ResponseEntity.ok(ApiResponse.success("Trạng thái nhân viên đã được cập nhật thành công", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
