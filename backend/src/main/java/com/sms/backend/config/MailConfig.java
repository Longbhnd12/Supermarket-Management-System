package com.sms.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * UC45 - loads the Gmail SMTP settings (application-mail.properties).
 * Credentials come from the MAIL_USERNAME / MAIL_PASSWORD environment variables.
 */
@Configuration
@PropertySource("classpath:application-mail.properties")
public class MailConfig {
}
