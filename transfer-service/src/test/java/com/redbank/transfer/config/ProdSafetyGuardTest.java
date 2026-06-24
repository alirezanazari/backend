package com.redbank.transfer.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProdSafetyGuardTest {
    private static final DebugProperties PROD_DEBUG = new DebugProperties(false, "debug-secret-with-enough-entropy");
    private static final JwtProperties PROD_JWT = new JwtProperties(
            "red-bank",
            "red-bank-api",
            "prod-jwt-signing-secret-with-enough-entropy"
    );
    private static final FeatureFlagClientProperties FEATURE_FLAGS =
            new FeatureFlagClientProperties(true, "http://feature.test", "feature-token");
    private static final PartyClientProperties PARTY =
            new PartyClientProperties(true, "http://account.test", "http://card.test", "party-token");

    @Test
    void prodRejectsDevelopLedgerInternalTransferToken() {
        ProdSafetyGuard guard = guard(new LedgerClientProperties(
                true,
                "http://ledger.test",
                LedgerClientProperties.DEVELOP_SERVICE_TOKEN
        ));

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transfer-service cannot start in prod with a develop ledger internal transfer token");
    }

    @Test
    void prodAllowsDedicatedLedgerInternalTransferToken() {
        ProdSafetyGuard guard = guard(new LedgerClientProperties(
                true,
                "http://ledger.test",
                "prod-internal-transfer-token"
        ));

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private ProdSafetyGuard guard(LedgerClientProperties ledger) {
        return new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                PROD_DEBUG,
                PROD_JWT,
                FEATURE_FLAGS,
                PARTY,
                ledger
        );
    }
}
