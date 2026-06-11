package com.redbank.pfm.config;

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
    private final AccountClientProperties accountClientProperties;
    private final LedgerClientProperties ledgerClientProperties;

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            AccountClientProperties accountClientProperties,
            LedgerClientProperties ledgerClientProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.accountClientProperties = accountClientProperties;
        this.ledgerClientProperties = ledgerClientProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (debugProperties.enabled()) {
            throw new IllegalStateException("pfm-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("pfm-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (jwtProperties.signingSecret().contains("local-develop")
                || jwtProperties.signingSecret().contains("change-me")) {
            throw new IllegalStateException("pfm-service cannot start in prod with a develop JWT signing secret");
        }
        if (!accountClientProperties.enabled()) {
            throw new IllegalStateException("pfm-service requires account integration in prod");
        }
        if (!ledgerClientProperties.enabled()) {
            throw new IllegalStateException("pfm-service requires ledger integration in prod");
        }
    }
}
