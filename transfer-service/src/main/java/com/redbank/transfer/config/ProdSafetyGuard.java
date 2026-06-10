package com.redbank.transfer.config;

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
    private final FeatureFlagClientProperties featureFlagClientProperties;
    private final PartyClientProperties partyClientProperties;
    private final LedgerClientProperties ledgerClientProperties;

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            FeatureFlagClientProperties featureFlagClientProperties,
            PartyClientProperties partyClientProperties,
            LedgerClientProperties ledgerClientProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.featureFlagClientProperties = featureFlagClientProperties;
        this.partyClientProperties = partyClientProperties;
        this.ledgerClientProperties = ledgerClientProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (debugProperties.enabled()) {
            throw new IllegalStateException("transfer-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("transfer-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (jwtProperties.signingSecret().contains("local-develop")
                || jwtProperties.signingSecret().contains("change-me")) {
            throw new IllegalStateException("transfer-service cannot start in prod with a develop JWT signing secret");
        }
        if (!featureFlagClientProperties.enabled()) {
            throw new IllegalStateException("transfer-service requires feature flag integration in prod");
        }
        if (!partyClientProperties.enabled()) {
            throw new IllegalStateException("transfer-service requires party validation in prod");
        }
        if (!ledgerClientProperties.enabled()) {
            throw new IllegalStateException("transfer-service requires ledger integration in prod");
        }
    }
}
