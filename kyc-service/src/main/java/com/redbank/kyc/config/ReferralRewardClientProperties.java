package com.redbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.referral-reward-client")
public record ReferralRewardClientProperties(
        boolean enabled,
        String baseUrl,
        String referralRewardToken
) {
    private static final String DEVELOP_BASE_URL = "http://localhost:8083";
    private static final String DEVELOP_SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    public ReferralRewardClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVELOP_BASE_URL;
        }
        if (referralRewardToken == null || referralRewardToken.isBlank()) {
            referralRewardToken = DEVELOP_SERVICE_TOKEN;
        }
    }
}
