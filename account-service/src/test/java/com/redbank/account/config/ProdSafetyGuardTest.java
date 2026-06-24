package com.redbank.account.config;

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

    @Test
    void prodRejectsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties(null, "prod-vault-create-token"));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account-service cannot start in prod with a develop internal service token");
    }

    @Test
    void prodAllowsCustomInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties(
                "prod-internal-token-from-secret-store",
                "prod-vault-create-token-from-secret-store"
        ));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void prodRejectsMissingJwtVerificationPublicKey() {
        ProdSafetyGuard guard = guard("prod", PROD_JWT_WITHOUT_PUBLIC_KEY, new InternalAccessProperties(
                "prod-internal-token-from-secret-store",
                "prod-vault-create-token-from-secret-store"
        ));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account-service requires an asymmetric JWT verification public key in prod");
    }

    @Test
    void nonProdAllowsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("develop", new InternalAccessProperties(null, null));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void prodRejectsDevelopVaultCreateToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties("prod-internal-token", null));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account-service cannot start in prod with a develop vault-create internal service token");
    }

    @Test
    void prodRejectsVaultCreateTokenThatReusesGeneralInternalToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties("same-prod-token", "same-prod-token"));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account-service cannot start in prod when vault-create token reuses the general internal service token");
    }

    private ProdSafetyGuard guard(String profile, InternalAccessProperties internalAccessProperties) {
        return guard(profile, PROD_JWT, internalAccessProperties);
    }

    private ProdSafetyGuard guard(String profile, JwtProperties jwtProperties, InternalAccessProperties internalAccessProperties) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
        environment.setActiveProfiles(profile);
        return new ProdSafetyGuard(environment, DEBUG_DISABLED, jwtProperties, internalAccessProperties);
    }
}
