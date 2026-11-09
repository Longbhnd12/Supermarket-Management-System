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

    /**
     * UC05: sends the login details (username + temporary password) of an account the Admin has just created.
     *
     * @param displayName       name used in the greeting (employee full name, supplier name or username)
     * @param temporaryPassword the plain temporary password; written into the email only, never into logs
     * @param active            false when the account was created Inactive (the email then says so)
     * @throws com.sms.backend.exception.BusinessException when the email cannot be sent
     */
    void sendNewAccountCredentials(String toEmail, String displayName, String username,
                                   String temporaryPassword, boolean active);
}
