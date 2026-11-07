package com.sms.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import static org.junit.jupiter.api.Assertions.*;

class RestSecurityErrorHandlerTest {

    private final RestSecurityErrorHandler handler = new RestSecurityErrorHandler();

    @Test
    void notLoggedInGives401Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"UNAUTHORIZED\""));
    }

    @Test
    void missingPermissionGives403WithMsg19() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("You do not have permission to perform this action."));
    }

    @Test
    void csrfFailureHasItsOwnCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), response, new MissingCsrfTokenException("abc"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"CSRF_INVALID\""));
    }
}
