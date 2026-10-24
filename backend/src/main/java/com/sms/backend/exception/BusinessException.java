package com.sms.backend.exception;

public class BusinessException
        extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}