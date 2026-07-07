package com.redbank.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.otp")
public record OtpProperties(
        long ttlSeconds,
        int maxAttempts,
        long resendCooldownSeconds,
        int maxPerPhonePerHour
) {
    public OtpProperties {
        if (ttlSeconds <= 0) {
            ttlSeconds = 300;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (resendCooldownSeconds <= 0) {
            resendCooldownSeconds = 45;
        }
        if (maxPerPhonePerHour <= 0) {
            maxPerPhonePerHour = 5;
        }
    }
}
