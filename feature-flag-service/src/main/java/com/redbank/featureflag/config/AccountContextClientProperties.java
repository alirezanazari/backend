package com.redbank.featureflag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.account-context-client")
public record AccountContextClientProperties(
        boolean enabled,
        String baseUrl,
        String serviceToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8082";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public AccountContextClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (serviceToken == null || serviceToken.isBlank()) {
            serviceToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
