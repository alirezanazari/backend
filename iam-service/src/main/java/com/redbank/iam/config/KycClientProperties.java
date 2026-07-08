package com.redbank.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.kyc-client")
public record KycClientProperties(
        String baseUrl,
        String completionGetTokenSecret
) {
    private static final String DEFAULT_BASE_URL = "http://localhost:8085";
    private static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public KycClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (completionGetTokenSecret == null || completionGetTokenSecret.isBlank()) {
            completionGetTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
    }
}
