package com.redbank.featureflag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String signingSecret
) {
}
