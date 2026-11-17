package com.redbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.feature-flag-client")
public record FeatureFlagClientProperties(
        String baseUrl,
        String serviceToken
) {
}
