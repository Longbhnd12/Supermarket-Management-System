package com.sms.backend.exception;

/**
 * UC01 - Login: thrown when the credentials are correct (or the account is locked)
 * but the account is not allowed to sign in right now (INACTIVE or LOCKED).
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class AccountAccessException extends RuntimeException {

    private final String errorCode;

    public AccountAccessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
