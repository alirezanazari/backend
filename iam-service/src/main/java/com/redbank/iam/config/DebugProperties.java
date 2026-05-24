package com.redbank.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.debug")
public record DebugProperties(
        boolean enabled,
        MockOtp mockOtp
) {
    public DebugProperties {
        if (mockOtp == null) {
            mockOtp = new MockOtp(false);
        }
    }

    public record MockOtp(boolean enabled) {
    }
}
