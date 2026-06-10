package com.redbank.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.party-client")
public record PartyClientProperties(
        boolean enabled,
        String accountBaseUrl,
        String cardBaseUrl,
        String serviceToken
) {
    private static final String DEVELOP_ACCOUNT_BASE_URL = "http://localhost:8082";
    private static final String DEVELOP_CARD_BASE_URL = "http://localhost:8084";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public PartyClientProperties {
        if (accountBaseUrl == null || accountBaseUrl.isBlank()) {
            accountBaseUrl = DEVELOP_ACCOUNT_BASE_URL;
        }
        if (cardBaseUrl == null || cardBaseUrl.isBlank()) {
            cardBaseUrl = DEVELOP_CARD_BASE_URL;
        }
        if (serviceToken == null || serviceToken.isBlank()) {
            serviceToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
