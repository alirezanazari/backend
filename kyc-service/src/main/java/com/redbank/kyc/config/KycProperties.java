package com.redbank.kyc.config;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.kyc")
public record KycProperties(
        BigDecimal referralRewardAmount,
        String referralRewardCurrency,
        boolean referralRewardEnabled,
        Boolean autoApproveEnabled
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
        // TODO(kyc-panel): auto-approve is a temporary stand-in until the admin KYC review panel
        // exists. It lazily approves any user's KYC so onboarding can complete without an operator.
        // Remove this flag (default it off) once the panel and admin approval flow ship.
        if (autoApproveEnabled == null) {
            autoApproveEnabled = true;
        }
    }
}
