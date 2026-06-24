package com.redbank.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.ledger-client")
public record LedgerClientProperties(
        boolean enabled,
        String baseUrl,
        String internalTransferToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8083";
    public static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public LedgerClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (internalTransferToken == null || internalTransferToken.isBlank()) {
            internalTransferToken = DEVELOP_SERVICE_TOKEN;
        }
    }

    public boolean usesDevelopInternalTransferToken() {
        return DEVELOP_SERVICE_TOKEN.equals(internalTransferToken);
    }
}
