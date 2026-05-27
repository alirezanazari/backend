package com.redbank.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.otp")
public record OtpProperties(
        long ttlSeconds,
        int maxAttempts
) {
    public OtpProperties {
        if (ttlSeconds <= 0) {
            ttlSeconds = 300;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
    }
}
