package com.redbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds
) {
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
    }
}
