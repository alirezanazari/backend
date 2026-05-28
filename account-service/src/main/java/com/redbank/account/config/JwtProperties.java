package com.redbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String signingSecret
) {
    private static final String DEVELOP_SIGNING_SECRET = "local-develop-debug-secret-change-me-32bytes";

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "red-bank-develop";
        }
        if (audience == null || audience.isBlank()) {
            audience = "red-bank-api";
        }
        if (signingSecret == null || signingSecret.isBlank()) {
            signingSecret = DEVELOP_SIGNING_SECRET;
        }
    }
}
