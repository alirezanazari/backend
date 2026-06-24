package com.redbank.card.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSafetyGuardTest {
    private static final DebugProperties DEBUG_DISABLED = new DebugProperties(false, "test-debug-secret-32bytes-minimum");
    private static final JwtProperties PROD_JWT = new JwtProperties(
            "red-bank",
            "red-bank-api",
            "prod-signing-secret-with-enough-entropy",
            "-----BEGIN PUBLIC KEY-----\n"
                    + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw/B3jRjA4lI0Vk5Aa6sA\n"
                    + "69W6r9ZKTeBc8nWueNnpxaPXQZ4fY9A7i6kVY9EGpUyQ54G6rLOJojrcLGMJD+87\n"
                    + "wAL60CEykV9MKM83xL0d3CkdbKy3E+Q8p1PPR/7Qlvtceo0MkWVHX6kBdyzZQ6We\n"
                    + "Qhb4Owd+gkNXxwgc7AqOLSnWIp2LQS9+xlrVLXHvuU0uC3pn4+u6GI6tC0wKN+WL\n"
                    + "g1+vpOQSTv9N6aSHLcSaOtfXJ7L/bAjVNI1Pj6fBvTQwDa2FWoH1Ns3Nz3KlwR5T\n"
                    + "41Lz3aHICgx4PBa4n0FPEk6FTKbVnnMdCXZaZ7GJsl70TdxOpbM84w+DTJ7+sgKG\n"
                    + "qQIDAQAB\n"
                    + "-----END PUBLIC KEY-----"
    );
    private static final JwtProperties PROD_JWT_WITHOUT_PUBLIC_KEY = new JwtProperties(
            "red-bank",
            "red-bank-api",
            "prod-signing-secret-with-enough-entropy",
            null
    );
    private static final AccountClientProperties ACCOUNT_CLIENT_ENABLED = new AccountClientProperties(
            true,
            "http://account.test",
            "prod-account-client-token"
    );
    private static final FeatureFlagClientProperties FEATURE_FLAG_CLIENT_ENABLED = new FeatureFlagClientProperties(
            true,
            "http://feature-flags.test",
            "prod-feature-flag-client-token"
    );

    @Test
    void prodRejectsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties(null));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("card-service cannot start in prod with a develop internal service token");
    }

    @Test
    void prodRejectsMissingJwtVerificationPublicKey() {
        ProdSafetyGuard guard = guard("prod", PROD_JWT_WITHOUT_PUBLIC_KEY, new InternalAccessProperties("prod-card-internal-token"));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("card-service requires an asymmetric JWT verification public key in prod");
    }

    @Test
    void prodAllowsCustomInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties("prod-card-internal-token"));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void nonProdAllowsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("develop", new InternalAccessProperties(null));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private ProdSafetyGuard guard(String profile, InternalAccessProperties internalAccessProperties) {
        return guard(profile, PROD_JWT, internalAccessProperties);
    }

    private ProdSafetyGuard guard(String profile, JwtProperties jwtProperties, InternalAccessProperties internalAccessProperties) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
        environment.setActiveProfiles(profile);
        return new ProdSafetyGuard(
                environment,
                DEBUG_DISABLED,
                jwtProperties,
                internalAccessProperties,
                ACCOUNT_CLIENT_ENABLED,
                FEATURE_FLAG_CLIENT_ENABLED
        );
    }
}
