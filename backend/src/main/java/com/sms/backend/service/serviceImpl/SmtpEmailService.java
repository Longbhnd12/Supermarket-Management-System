package com.sms.backend.service.serviceImpl;

import com.sms.backend.exception.BusinessException;
import com.sms.backend.service.EmailService;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email Service over SMTP (Gmail). Reset codes and temporary passwords are written into the email only,
 * never into logs (SRS 4.2.4 Data confidentiality).
 */
@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    public static final String MSG_SEND_FAILED = "Unable to send the reset code email. Please try again later.";
    public static final String MSG_ACCOUNT_EMAIL_FAILED = "Unable to send the account details email.";
    static final String DEFAULT_LOGIN_URL = "http://localhost:5173/login";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final String loginUrl;

    @Autowired
    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.username:}") String fromAddress,
                            @Value("${sms.mail.from-name:Supermarket Management System}") String fromName,
                            @Value("${sms.frontend.login-url:" + DEFAULT_LOGIN_URL + "}") String loginUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.loginUrl = loginUrl == null || loginUrl.isBlank() ? DEFAULT_LOGIN_URL : loginUrl.trim();
    }

    public SmtpEmailService(JavaMailSender mailSender, String fromAddress, String fromName) {
        this(mailSender, fromAddress, fromName, DEFAULT_LOGIN_URL);
    }

    // ---------------- UC45 - Forgot Password ----------------

    @Override
    public void sendPasswordResetCode(String toEmail, String username, String code, int validMinutes) {
        send("UC45", "password reset code", MSG_SEND_FAILED, toEmail, "Your password reset code",
                resetText(username, code, validMinutes), resetHtml(username, code, validMinutes));
    }

    private String resetText(String username, String code, int validMinutes) {
        return "Hello " + username + ",\n\n"
                + "We received a request to reset the password of your Supermarket Management System account.\n\n"
                + "Your reset code is: " + code + "\n\n"
                + "The code is valid for " + validMinutes + " minutes and can be used only once.\n"
                + "If you did not request a password reset, you can ignore this email; your password will not change.\n";
    }

    private String resetHtml(String username, String code, int validMinutes) {
        return "<div style=\"font-family:Arial,sans-serif;color:#19233b;max-width:480px\">"
                + "<h2 style=\"margin:0 0 12px\">Password reset</h2>"
                + "<p>Hello <b>" + escape(username) + "</b>,</p>"
                + "<p>We received a request to reset the password of your Supermarket Management System account.</p>"
                + "<p style=\"margin:20px 0\">Your reset code is:</p>"
                + "<p style=\"font-size:30px;font-weight:bold;letter-spacing:6px;margin:0 0 20px;color:#2458dc\">" + escape(code) + "</p>"
                + "<p>The code is valid for <b>" + validMinutes + " minutes</b> and can be used only once.</p>"
                + "<p style=\"color:#69748c;font-size:13px\">If you did not request a password reset, you can ignore this email; "
                + "your password will not change.</p></div>";
    }

    // ---------------- UC05 - Manage Users: login details of a new account ----------------

    @Override
    public void sendNewAccountCredentials(String toEmail, String displayName, String username,
                                          String temporaryPassword, boolean active) {
        String name = displayName == null || displayName.isBlank() ? username : displayName;
        send("UC05", "account details", MSG_ACCOUNT_EMAIL_FAILED, toEmail, "Your Supermarket Management System account",
                accountText(name, username, temporaryPassword, active), accountHtml(name, username, temporaryPassword, active));
    }

    private String accountText(String name, String username, String password, boolean active) {
        return "Hello " + name + ",\n\n"
                + "An administrator has created an account for you in the Supermarket Management System.\n\n"
                + "Username: " + username + "\n"
                + "Temporary password: " + password + "\n"
                + "Sign in at: " + loginUrl + "\n\n"
                + (active ? "" : "Your account is not active yet. You can sign in once an administrator activates it.\n\n")
                + "For your security, please change this password after your first sign-in "
                + "(account menu > Change Password) and do not share it with anyone.\n";
    }

    private String accountHtml(String name, String username, String password, boolean active) {
        String row = "<tr><td style=\"padding:6px 16px 6px 0;color:#69748c\">%s</td>"
                + "<td style=\"padding:6px 0;font-weight:bold;font-family:Consolas,monospace;font-size:15px\">%s</td></tr>";
        return "<div style=\"font-family:Arial,sans-serif;color:#19233b;max-width:520px\">"
                + "<h2 style=\"margin:0 0 12px\">Welcome to Supermarket Management System</h2>"
                + "<p>Hello <b>" + escape(name) + "</b>,</p>"
                + "<p>An administrator has created an account for you. Here are your login details:</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0\">"
                + String.format(row, "Username", escape(username))
                + String.format(row, "Temporary password", escape(password))
                + "</table>"
                + "<p style=\"margin:20px 0\"><a href=\"" + escape(loginUrl) + "\" style=\"background:#2458dc;color:#ffffff;"
                + "padding:10px 18px;border-radius:6px;text-decoration:none;font-weight:bold\">Sign in</a></p>"
                + (active ? "" : "<p style=\"color:#b54708\">Your account is not active yet. "
                        + "You can sign in once an administrator activates it.</p>")
                + "<p style=\"color:#69748c;font-size:13px\">For your security, please change this password after your first "
                + "sign-in (account menu &gt; Change Password) and do not share it with anyone.</p></div>";
    }

    // ---------------- shared ----------------

    private void send(String useCase, String what, String failureMessage,
                      String toEmail, String subject, String text, String html) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.error("{}: cannot send email - the MAIL_USERNAME / MAIL_PASSWORD environment variables are not set", useCase);
            throw new BusinessException(failureMessage);
        }
        try {
            var message = mailSender.createMimeMessage();
            // multipart = true: required to send both a plain-text and an HTML version (setText(plain, html))
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("{}: {} email sent to {}", useCase, what, toEmail);
        } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException exception) {
            log.error("{}: sending the {} email to {} failed: {}", useCase, what, toEmail, exception.getMessage());
            throw new BusinessException(failureMessage);
        } catch (RuntimeException exception) {
            // Any other problem while building the email: still a clear message for the user, full detail in the log
            log.error("{}: unexpected error while building the {} email for {}", useCase, what, toEmail, exception);
            throw new BusinessException(failureMessage);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
