package com.redbank.pfm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.ledger-client")
public record LedgerClientProperties(
        boolean enabled,
        String baseUrl,
        String serviceToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8083";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public LedgerClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (serviceToken == null || serviceToken.isBlank()) {
            serviceToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
