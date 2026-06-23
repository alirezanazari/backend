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
            "prod-signing-secret-with-enough-entropy"
    );

    @Test
    void prodRejectsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties(null));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account-service cannot start in prod with a develop internal service token");
    }

    @Test
    void prodAllowsCustomInternalServiceToken() {
        ProdSafetyGuard guard = guard("prod", new InternalAccessProperties("prod-internal-token-from-secret-store"));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void nonProdAllowsDevelopInternalServiceToken() {
        ProdSafetyGuard guard = guard("develop", new InternalAccessProperties(null));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private ProdSafetyGuard guard(String profile, InternalAccessProperties internalAccessProperties) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
        environment.setActiveProfiles(profile);
        return new ProdSafetyGuard(environment, DEBUG_DISABLED, PROD_JWT, internalAccessProperties);
    }
}
