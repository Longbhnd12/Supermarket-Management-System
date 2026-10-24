package com.sms.backend.exception;

public class BadRequestException
        extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}