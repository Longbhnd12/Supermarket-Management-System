package com.sms.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@PropertySource("classpath:application-payment.properties")
@EnableScheduling
@EnableConfigurationProperties(VnpayProperties.class)
public class PaymentConfig {
    @Bean
    public Clock paymentClock() { return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")); }
}
