package com.sms.backend.service.serviceImpl;

import com.sms.backend.exception.BusinessException;
import com.sms.backend.service.EmailService;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email Service over SMTP (Gmail). The reset code is written into the email only,
 * never into logs (SRS 4.2.4 Data confidentiality).
 */
@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    public static final String MSG_SEND_FAILED = "Unable to send the reset code email. Please try again later.";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.username:}") String fromAddress,
                            @Value("${sms.mail.from-name:Supermarket Management System}") String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String username, String code, int validMinutes) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.error("UC45: cannot send email - the MAIL_USERNAME / MAIL_PASSWORD environment variables are not set");
            throw new BusinessException(MSG_SEND_FAILED);
        }
        try {
            var message = mailSender.createMimeMessage();
            // multipart = true: required to send both a plain-text and an HTML version (setText(plain, html))
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject("Your password reset code");
            helper.setText(buildText(username, code, validMinutes), buildHtml(username, code, validMinutes));
            mailSender.send(message);
            log.info("UC45: password reset code email sent to {}", toEmail);
        } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException exception) {
            log.error("UC45: sending the reset code email to {} failed: {}", toEmail, exception.getMessage());
            throw new BusinessException(MSG_SEND_FAILED);
        } catch (RuntimeException exception) {
            // Any other problem while building the email: still a clear message for the user, full detail in the log
            log.error("UC45: unexpected error while building the reset code email for {}", toEmail, exception);
            throw new BusinessException(MSG_SEND_FAILED);
        }
    }

    private String buildText(String username, String code, int validMinutes) {
        return "Hello " + username + ",\n\n"
                + "We received a request to reset the password of your Supermarket Management System account.\n\n"
                + "Your reset code is: " + code + "\n\n"
                + "The code is valid for " + validMinutes + " minutes and can be used only once.\n"
                + "If you did not request a password reset, you can ignore this email; your password will not change.\n";
    }

    private String buildHtml(String username, String code, int validMinutes) {
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

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
