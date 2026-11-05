package com.sms.backend.service;

/**
 * Sends emails through the external Email Service (SRS 4.1.3).
 * Kept behind an interface so the provider can be replaced (SRS 4.2.5).
 */
public interface EmailService {

    /**
     * UC45: sends the one-time password reset code.
     *
     * @throws com.sms.backend.exception.BusinessException when the email cannot be sent
     */
    void sendPasswordResetCode(String toEmail, String username, String code, int validMinutes);
}
