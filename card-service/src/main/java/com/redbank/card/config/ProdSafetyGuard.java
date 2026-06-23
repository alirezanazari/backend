package com.redbank.card.config;

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
    private final InternalAccessProperties internalAccessProperties;
    private final AccountClientProperties accountClientProperties;
    private final FeatureFlagClientProperties featureFlagClientProperties;

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            InternalAccessProperties internalAccessProperties,
            AccountClientProperties accountClientProperties,
            FeatureFlagClientProperties featureFlagClientProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.internalAccessProperties = internalAccessProperties;
        this.accountClientProperties = accountClientProperties;
        this.featureFlagClientProperties = featureFlagClientProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (debugProperties.enabled()) {
            throw new IllegalStateException("card-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("card-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (jwtProperties.signingSecret().contains("local-develop")
                || jwtProperties.signingSecret().contains("change-me")) {
            throw new IllegalStateException("card-service cannot start in prod with a develop JWT signing secret");
        }
        if (internalAccessProperties.serviceTokenSecret() == null
                || internalAccessProperties.serviceTokenSecret().isBlank()
                || internalAccessProperties.usesDevelopServiceTokenSecret()) {
            throw new IllegalStateException("card-service cannot start in prod with a develop internal service token");
        }
        if (!accountClientProperties.enabled()) {
            throw new IllegalStateException("card-service requires account validation in prod");
        }
        if (!featureFlagClientProperties.enabled()) {
            throw new IllegalStateException("card-service requires feature flag integration in prod");
        }
    }
}
