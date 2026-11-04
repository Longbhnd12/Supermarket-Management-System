package com.sms.backend.dto.request;

/**
 * SRS 4.2.4 Password rules: at least 8 characters, including uppercase, lowercase and a digit.
 * Used by UC05 (temporary password) and later by UC03 / UC45.
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,255}$";
    public static final String MESSAGE =
            "Password must be at least 8 characters and include uppercase, lowercase and a digit";

    private PasswordPolicy() {
    }
}
