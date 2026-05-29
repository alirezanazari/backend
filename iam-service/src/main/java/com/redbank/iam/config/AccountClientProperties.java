package com.redbank.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.account-client")
public record AccountClientProperties(
        String baseUrl,
        String internalServiceTokenSecret
) {
    private static final String DEFAULT_BASE_URL = "http://localhost:8082";
    private static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public AccountClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (internalServiceTokenSecret == null || internalServiceTokenSecret.isBlank()) {
            internalServiceTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
    }
}
