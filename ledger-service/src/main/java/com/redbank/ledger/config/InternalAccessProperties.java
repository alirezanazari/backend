package com.redbank.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret
) {
    private static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public InternalAccessProperties {
        if (serviceTokenSecret == null || serviceTokenSecret.isBlank()) {
            serviceTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
    }
}
