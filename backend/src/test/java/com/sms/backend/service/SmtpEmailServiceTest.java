package com.sms.backend.service;

import com.sms.backend.exception.BusinessException;
import com.sms.backend.service.serviceImpl.SmtpEmailService;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UC45 - builds a REAL MimeMessage (only the SMTP sending is mocked),
 * so mistakes in how the email is put together are caught here.
 */
class SmtpEmailServiceTest {

    private JavaMailSender senderReturningRealMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        return sender;
    }

    @Test
    void buildsAndSendsEmailContainingTheCode() throws Exception {
        JavaMailSender sender = senderReturningRealMessage();
        SmtpEmailService service = new SmtpEmailService(sender, "sms.project.test@gmail.com", "SMS");

        assertDoesNotThrow(() -> service.sendPasswordResetCode("cashier01@sms.vn", "cashier01", "482913", 10));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("Your password reset code", sent.getSubject());
        assertEquals("cashier01@sms.vn", sent.getAllRecipients()[0].toString());
        assertTrue(sent.getFrom()[0].toString().contains("sms.project.test@gmail.com"));

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent.writeTo(raw);
        String content = raw.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("482913"), "the code must be in the email");
        assertTrue(content.contains("text/plain") && content.contains("text/html"), "plain-text and HTML versions");
    }

    @Test
    void missingGmailSettingsGiveAClearMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailService service = new SmtpEmailService(sender, "", "SMS");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendPasswordResetCode("cashier01@sms.vn", "cashier01", "482913", 10));
        assertEquals(SmtpEmailService.MSG_SEND_FAILED, error.getMessage());
        verifyNoInteractions(sender);
    }

    @Test
    void smtpFailureGivesAClearMessage() {
        JavaMailSender sender = senderReturningRealMessage();
        doThrow(new MailSendException("Gmail unreachable")).when(sender).send(any(MimeMessage.class));
        SmtpEmailService service = new SmtpEmailService(sender, "sms.project.test@gmail.com", "SMS");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendPasswordResetCode("cashier01@sms.vn", "cashier01", "482913", 10));
        assertEquals(SmtpEmailService.MSG_SEND_FAILED, error.getMessage());
    }

    // ---------- UC05: login details of a new account ----------

    @Test
    void newAccountEmailContainsUsernamePasswordAndSignInLink() throws Exception {
        JavaMailSender sender = senderReturningRealMessage();
        SmtpEmailService service = new SmtpEmailService(sender, "sms.project.test@gmail.com", "SMS",
                "http://localhost:5173/login");

        assertDoesNotThrow(() -> service.sendNewAccountCredentials("mai.dt@sms.vn", "Do Thi Mai", "cashier03",
                "Temp<Pass>1", true));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("Your Supermarket Management System account", sent.getSubject());
        assertEquals("mai.dt@sms.vn", sent.getAllRecipients()[0].toString());

        String content = rawContent(sent);
        assertTrue(content.contains("cashier03"), "the username must be in the email");
        assertTrue(content.contains("Temp<Pass>1"), "plain-text part has the password as typed");
        assertTrue(content.contains("Temp&lt;Pass&gt;1"), "HTML part has the password escaped");
        assertTrue(content.contains("http://localhost:5173/login"), "the sign-in link must be in the email");
        assertFalse(content.contains("not active yet"));
    }

    @Test
    void inactiveAccountEmailWarnsThatTheAccountIsNotActiveYet() throws Exception {
        JavaMailSender sender = senderReturningRealMessage();
        SmtpEmailService service = new SmtpEmailService(sender, "sms.project.test@gmail.com", "SMS");

        service.sendNewAccountCredentials("mai.dt@sms.vn", null, "cashier03", "Secret123", false);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        String content = rawContent(captor.getValue());
        assertTrue(content.contains("not active yet"));
        assertTrue(content.contains("Hello cashier03"), "falls back to the username when there is no name");
    }

    @Test
    void newAccountEmailFailureGivesItsOwnMessage() {
        JavaMailSender sender = senderReturningRealMessage();
        doThrow(new MailSendException("Gmail unreachable")).when(sender).send(any(MimeMessage.class));
        SmtpEmailService service = new SmtpEmailService(sender, "sms.project.test@gmail.com", "SMS");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendNewAccountCredentials("mai.dt@sms.vn", "Do Thi Mai", "cashier03", "Secret123", true));
        assertEquals(SmtpEmailService.MSG_ACCOUNT_EMAIL_FAILED, error.getMessage());
    }

    /** Decoded text of every text part (plain + HTML), so quoted-printable line breaks do not matter. */
    private String rawContent(MimeMessage message) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        MimeMessage parsed = new MimeMessage((Session) null, new ByteArrayInputStream(raw.toByteArray()));
        StringBuilder text = new StringBuilder();
        collectText(parsed.getContent(), text);
        return text.toString();
    }

    private void collectText(Object content, StringBuilder text) throws Exception {
        if (content instanceof String part) {
            text.append(part).append('\n');
        } else if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collectText(multipart.getBodyPart(i).getContent(), text);
            }
        }
    }
}
