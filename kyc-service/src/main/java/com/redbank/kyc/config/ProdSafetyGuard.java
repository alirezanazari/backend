package com.redbank.kyc.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProdSafetyGuard implements ApplicationRunner {
    private final Environment environment;
    private final DebugProperties debugProperties;
    private final JwtProperties jwtProperties;
    private final ReferrerAccountClientProperties accountClientProperties;
    private final ReferralRewardClientProperties rewardClientProperties;

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            ReferrerAccountClientProperties accountClientProperties,
            ReferralRewardClientProperties rewardClientProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.accountClientProperties = accountClientProperties;
        this.rewardClientProperties = rewardClientProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (debugProperties.enabled()) {
            throw new IllegalStateException("kyc-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("kyc-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (jwtProperties.signingSecret().contains("local-develop")
                || jwtProperties.signingSecret().contains("change-me")) {
            throw new IllegalStateException("kyc-service cannot start in prod with a develop JWT signing secret");
        }
        if (!accountClientProperties.enabled() || !rewardClientProperties.enabled()) {
            throw new IllegalStateException("kyc-service requires referral integrations in prod");
        }
    }
}
