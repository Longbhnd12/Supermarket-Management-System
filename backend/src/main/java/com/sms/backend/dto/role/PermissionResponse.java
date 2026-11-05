package com.sms.backend.dto.role;

/**
 * UC06: one permission of the catalog (shown as a checkbox).
 */
public record PermissionResponse(String code, String label, String group) {
}
