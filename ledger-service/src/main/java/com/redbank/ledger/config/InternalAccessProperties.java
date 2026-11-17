package com.redbank.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret,
        String internalTransferTokenSecret,
        String referralRewardTokenSecret,
        String accountFundingTokenSecret
) {
    public static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public InternalAccessProperties {
        if (serviceTokenSecret == null || serviceTokenSecret.isBlank()) {
            serviceTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
        if (internalTransferTokenSecret == null || internalTransferTokenSecret.isBlank()) {
            internalTransferTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
        if (referralRewardTokenSecret == null || referralRewardTokenSecret.isBlank()) {
            referralRewardTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
        if (accountFundingTokenSecret == null || accountFundingTokenSecret.isBlank()) {
            accountFundingTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
    }

    public boolean usesDevelopServiceTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(serviceTokenSecret);
    }

    public boolean usesDevelopReferralRewardTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(referralRewardTokenSecret);
    }

    public boolean usesDevelopInternalTransferTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(internalTransferTokenSecret);
    }

    public boolean internalTransferTokenReusesServiceTokenSecret() {
        return serviceTokenSecret.equals(internalTransferTokenSecret);
    }

    public boolean referralRewardTokenReusesServiceTokenSecret() {
        return serviceTokenSecret.equals(referralRewardTokenSecret);
    }
    public boolean usesDevelopAccountFundingTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(accountFundingTokenSecret);
    }
}
