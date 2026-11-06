package com.sms.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads the JWT settings (application-jwt.properties). The secret comes from the JWT_SECRET environment variable.
 */
@Configuration
@PropertySource("classpath:application-jwt.properties")
public class JwtConfig {
}
