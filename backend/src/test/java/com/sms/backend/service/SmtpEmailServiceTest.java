package com.sms.backend.service;

import com.sms.backend.exception.BusinessException;
import com.sms.backend.service.serviceImpl.SmtpEmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

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
}
