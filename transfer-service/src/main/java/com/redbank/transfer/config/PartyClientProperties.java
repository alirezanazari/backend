package com.redbank.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "redbank.party-client")
public record PartyClientProperties(
        boolean enabled,
        String accountBaseUrl,
        String cardBaseUrl,
        String serviceToken,
        String accountTransferResolveToken
) {
    private static final String DEVELOP_ACCOUNT_BASE_URL = "http://localhost:8082";
    private static final String DEVELOP_CARD_BASE_URL = "http://localhost:8084";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public PartyClientProperties(boolean enabled, String accountBaseUrl, String cardBaseUrl, String serviceToken) {
        this(enabled, accountBaseUrl, cardBaseUrl, serviceToken, null);
    }

    @ConstructorBinding
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
        if (accountTransferResolveToken == null || accountTransferResolveToken.isBlank()) {
            accountTransferResolveToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
