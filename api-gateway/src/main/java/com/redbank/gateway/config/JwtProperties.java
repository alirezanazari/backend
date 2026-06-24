package com.redbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds,
        String signingSecret,
        String verificationPublicKey
) {
    private static final String DEVELOP_SIGNING_SECRET = "local-develop-debug-secret-change-me-32bytes";

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "red-bank-develop";
        }
        if (audience == null || audience.isBlank()) {
            audience = "red-bank-api";
        }
        if (accessTokenTtlSeconds <= 0) {
            accessTokenTtlSeconds = 900;
        }
        if (signingSecret == null || signingSecret.isBlank()) {
            signingSecret = DEVELOP_SIGNING_SECRET;
        }
    }

    public boolean usesAsymmetricVerification() {
        return verificationPublicKey != null && !verificationPublicKey.isBlank();
    }
}
