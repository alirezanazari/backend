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
        requireScopedInternalToken(
                internalAccessProperties.completionGetTokenSecret(),
                "completion-get"
        );
        requireScopedInternalToken(
                internalAccessProperties.cardEligibilityGetTokenSecret(),
                "card-eligibility-get"
        );
        requireScopedInternalToken(
                internalAccessProperties.referralRewardAccountGetTokenSecret(),
                "referral-reward-account-get"
        );
        requireScopedInternalToken(
                internalAccessProperties.featureEvaluationContextGetTokenSecret(),
                "feature-evaluation-context-get"
        );
        requireScopedInternalToken(
                internalAccessProperties.transferResolveTokenSecret(),
                "transfer-resolve"
        );
    }

    private void requireScopedInternalToken(String token, String tokenName) {
        if (token == null
                || token.isBlank()
                || InternalAccessProperties.DEVELOP_SERVICE_TOKEN_SECRET.equals(token)) {
            throw new IllegalStateException(
                    "account-service cannot start in prod with a develop " + tokenName + " internal service token"
            );
        }
        if (internalAccessProperties.serviceTokenSecret().equals(token)) {
            throw new IllegalStateException(
                    "account-service cannot start in prod when "
                            + tokenName
                            + " token reuses the general internal service token"
            );
        }
    }
}
