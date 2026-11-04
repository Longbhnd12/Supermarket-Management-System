package com.sms.backend.dto.response;

public record AuthenticatedUserResponse(Long userId, String username, String email, String role) {
}
