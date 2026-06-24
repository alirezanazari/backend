package com.redbank.account.config;

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

    public ProdSafetyGuard(
            Environment environment,
            DebugProperties debugProperties,
            JwtProperties jwtProperties,
            InternalAccessProperties internalAccessProperties
    ) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.internalAccessProperties = internalAccessProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }

        if (debugProperties.enabled()) {
            throw new IllegalStateException("account-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("account-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (!jwtProperties.usesAsymmetricVerification()) {
            throw new IllegalStateException("account-service requires an asymmetric JWT verification public key in prod");
        }
        if (internalAccessProperties.serviceTokenSecret() == null
                || internalAccessProperties.serviceTokenSecret().isBlank()
                || internalAccessProperties.usesDevelopServiceTokenSecret()) {
            throw new IllegalStateException("account-service cannot start in prod with a develop internal service token");
        }
        if (internalAccessProperties.vaultCreateTokenSecret() == null
                || internalAccessProperties.vaultCreateTokenSecret().isBlank()
                || internalAccessProperties.usesDevelopVaultCreateTokenSecret()) {
            throw new IllegalStateException("account-service cannot start in prod with a develop vault-create internal service token");
        }
        if (internalAccessProperties.vaultCreateTokenReusesServiceTokenSecret()) {
            throw new IllegalStateException("account-service cannot start in prod when vault-create token reuses the general internal service token");
        }
    }
}
