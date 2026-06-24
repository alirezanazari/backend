package com.redbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret,
        String vaultCreateTokenSecret,
        String completionGetTokenSecret,
        String cardEligibilityGetTokenSecret,
        String referralRewardAccountGetTokenSecret,
        String featureEvaluationContextGetTokenSecret,
        String transferResolveTokenSecret
) {
    public static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public InternalAccessProperties(String serviceTokenSecret, String vaultCreateTokenSecret) {
        this(serviceTokenSecret, vaultCreateTokenSecret, null, null, null, null, null);
    }

    public InternalAccessProperties {
        serviceTokenSecret = defaultIfBlank(serviceTokenSecret);
        vaultCreateTokenSecret = defaultIfBlank(vaultCreateTokenSecret);
        completionGetTokenSecret = defaultIfBlank(completionGetTokenSecret);
        cardEligibilityGetTokenSecret = defaultIfBlank(cardEligibilityGetTokenSecret);
        referralRewardAccountGetTokenSecret = defaultIfBlank(referralRewardAccountGetTokenSecret);
        featureEvaluationContextGetTokenSecret = defaultIfBlank(featureEvaluationContextGetTokenSecret);
        transferResolveTokenSecret = defaultIfBlank(transferResolveTokenSecret);
    }

    public boolean usesDevelopServiceTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(serviceTokenSecret);
    }

    public boolean usesDevelopVaultCreateTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(vaultCreateTokenSecret);
    }

    public boolean vaultCreateTokenReusesServiceTokenSecret() {
        return serviceTokenSecret.equals(vaultCreateTokenSecret);
    }

    private static String defaultIfBlank(String value) {
        return value == null || value.isBlank() ? DEVELOP_SERVICE_TOKEN_SECRET : value;
    }
}
