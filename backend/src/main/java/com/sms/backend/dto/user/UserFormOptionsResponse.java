package com.sms.backend.dto.user;

import java.util.List;

/**
 * UC05: data for the dropdowns of the Create/Edit User screen.
 * Employees / suppliers that are already linked to another account are not listed.
 */
public record UserFormOptionsResponse(
        List<RoleOption> roles,
        List<EmployeeOption> employees,
        List<SupplierOption> suppliers
) {
    public record RoleOption(Long roleId, String roleName) {
    }

    public record EmployeeOption(Long employeeId, String employeeCode, String fullName, String position) {
    }

    public record SupplierOption(Long supplierId, String supplierCode, String supplierName) {
    }
}
