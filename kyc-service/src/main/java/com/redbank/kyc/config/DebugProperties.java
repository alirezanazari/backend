package com.redbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.debug")
public record DebugProperties(
        boolean enabled,
        String jwtSecret
) {
    private static final String DEVELOP_SECRET = "local-develop-debug-secret-change-me-32bytes";

    public DebugProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = DEVELOP_SECRET;
        }
    }
}
