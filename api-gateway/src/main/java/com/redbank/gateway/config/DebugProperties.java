package com.redbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.debug")
public record DebugProperties(
        boolean enabled,
        String jwtSecret,
        MockOtp mockOtp
) {
    public DebugProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "local-develop-debug-secret-change-me-32bytes";
        }
        if (mockOtp == null) {
            mockOtp = new MockOtp(false);
        }
    }

    public record MockOtp(boolean enabled) {
    }
}
