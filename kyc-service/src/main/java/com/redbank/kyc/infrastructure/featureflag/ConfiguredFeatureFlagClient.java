package com.redbank.kyc.infrastructure.featureflag;

import com.redbank.kyc.application.FeatureFlagClient;
import com.redbank.kyc.config.KycProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "redbank.feature-flag-client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class ConfiguredFeatureFlagClient implements FeatureFlagClient {
    private static final String REFERRAL_REWARD_FLAG = "referral.reward.enabled";

    private final KycProperties kycProperties;

    public ConfiguredFeatureFlagClient(KycProperties kycProperties) {
        this.kycProperties = kycProperties;
    }

    @Override
    public boolean isEnabled(String key) {
        return !REFERRAL_REWARD_FLAG.equals(key) || kycProperties.referralRewardEnabled();
    }
}
