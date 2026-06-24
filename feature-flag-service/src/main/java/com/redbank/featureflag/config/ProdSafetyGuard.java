package com.redbank.featureflag.config;

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
    private final AccountContextClientProperties accountContextClientProperties;

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            InternalAccessProperties internalAccessProperties,
            AccountContextClientProperties accountContextClientProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.internalAccessProperties = internalAccessProperties;
        this.accountContextClientProperties = accountContextClientProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (debugProperties.enabled()) {
            throw new IllegalStateException(
                    "feature-flag-service cannot start in prod with redbank.debug.enabled=true"
            );
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException(
                    "feature-flag-service cannot start in prod with a develop/debug JWT issuer"
            );
        }
        if (!jwtProperties.usesAsymmetricVerification()) {
            throw new IllegalStateException(
                    "feature-flag-service requires an asymmetric JWT verification public key in prod"
            );
        }
        if (internalAccessProperties.serviceTokenSecret().contains("local-develop")
                || internalAccessProperties.serviceTokenSecret().contains("change-me")) {
            throw new IllegalStateException(
                    "feature-flag-service cannot start in prod with a develop internal service token"
            );
        }
        if (!accountContextClientProperties.enabled()) {
            throw new IllegalStateException(
                    "feature-flag-service requires account context integration in prod"
            );
        }
    }
}
