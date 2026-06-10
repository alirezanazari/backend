package com.redbank.kyc.config;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.kyc")
public record KycProperties(
        BigDecimal referralRewardAmount,
        String referralRewardCurrency
) {
    public KycProperties {
        if (referralRewardAmount == null || referralRewardAmount.signum() <= 0) {
            referralRewardAmount = new BigDecimal("500000");
        }
        if (referralRewardCurrency == null || !referralRewardCurrency.matches("[A-Za-z]{3}")) {
            referralRewardCurrency = "IRR";
        } else {
            referralRewardCurrency = referralRewardCurrency.toUpperCase(Locale.ROOT);
        }
    }
}
