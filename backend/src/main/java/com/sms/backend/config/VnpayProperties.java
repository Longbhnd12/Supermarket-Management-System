package com.sms.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment.vnpay")
public class VnpayProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String returnUrl = "";
    private String ipnUrl = "";
    private Set<String> enabledBankCodes = new HashSet<>();
    private int expiryMinutes = 15;
    private String serverIp = "127.0.0.1";
}
