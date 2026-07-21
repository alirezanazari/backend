package com.redbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "redbank.iam-client")
public record IamClientProperties(
        boolean enabled,
        String baseUrl,
        String otpChallengeCreateToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8081";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    @ConstructorBinding
    public IamClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (otpChallengeCreateToken == null || otpChallengeCreateToken.isBlank()) {
            otpChallengeCreateToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
