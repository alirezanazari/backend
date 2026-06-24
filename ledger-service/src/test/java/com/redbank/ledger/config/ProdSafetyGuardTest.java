package com.redbank.ledger.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProdSafetyGuardTest {
    @Test
    void prodRejectsDevelopReferralRewardToken() {
        ProdSafetyGuard guard = new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new InternalAccessProperties(
                        "prod-general-token",
                        "prod-internal-transfer-token",
                        InternalAccessProperties.DEVELOP_SERVICE_TOKEN_SECRET
                )
        );

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger-service cannot start in prod with a develop referral reward token");
    }

    @Test
    void prodRejectsReferralRewardTokenReusingGeneralServiceToken() {
        ProdSafetyGuard guard = new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new InternalAccessProperties("shared-prod-token", "prod-internal-transfer-token", "shared-prod-token")
        );

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger-service referral reward token must not reuse the general service token");
    }

    @Test
    void prodRejectsDevelopInternalTransferToken() {
        ProdSafetyGuard guard = new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new InternalAccessProperties(
                        "prod-general-token",
                        InternalAccessProperties.DEVELOP_SERVICE_TOKEN_SECRET,
                        "prod-referral-reward-token"
                )
        );

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger-service cannot start in prod with a develop internal transfer token");
    }

    @Test
    void prodRejectsInternalTransferTokenReusingGeneralServiceToken() {
        ProdSafetyGuard guard = new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new InternalAccessProperties("shared-prod-token", "shared-prod-token", "prod-referral-reward-token")
        );

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger-service internal transfer token must not reuse the general service token");
    }

    @Test
    void prodAllowsDistinctNonDefaultInternalTokens() {
        ProdSafetyGuard guard = new ProdSafetyGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new InternalAccessProperties(
                        "prod-general-token",
                        "prod-internal-transfer-token",
                        "prod-referral-reward-token"
                )
        );

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }
}
