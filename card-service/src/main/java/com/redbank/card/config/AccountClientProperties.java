package com.redbank.card.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "redbank.account-client")
public record AccountClientProperties(
        boolean enabled,
        String baseUrl,
        String serviceToken,
        String cardEligibilityGetToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8082";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public AccountClientProperties(boolean enabled, String baseUrl, String serviceToken) {
        this(enabled, baseUrl, serviceToken, null);
    }

    @ConstructorBinding
    public AccountClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (serviceToken == null || serviceToken.isBlank()) {
            serviceToken = DEVELOP_SERVICE_TOKEN;
        }
        if (cardEligibilityGetToken == null || cardEligibilityGetToken.isBlank()) {
            cardEligibilityGetToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
