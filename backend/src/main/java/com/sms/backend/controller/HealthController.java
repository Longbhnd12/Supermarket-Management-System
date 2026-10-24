package com.sms.backend.controller;

import com.sms.backend.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "SMS Backend is running";
    }

    @GetMapping("/test-error")
    public String testError() {
        throw new ResourceNotFoundException("Test resource not found");
    }
}