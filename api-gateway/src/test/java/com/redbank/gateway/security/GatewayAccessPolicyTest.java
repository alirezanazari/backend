package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class GatewayAccessPolicyTest {
    private final GatewayAccessPolicy policy = new GatewayAccessPolicy();

    @Test
    void mapsDocumentedPublicAndProtectedEndpoints() {
        assertThat(policy.access(HttpMethod.POST, "/api/v1/iam/login"))
                .isEqualTo(GatewayAccessPolicy.Access.PUBLIC);
        assertThat(policy.access(HttpMethod.POST, "/api/v1/account/register"))
                .isEqualTo(GatewayAccessPolicy.Access.ONBOARDING_ACCOUNT);
        assertThat(policy.access(HttpMethod.POST, "/api/v1/card/unblock"))
                .isEqualTo(GatewayAccessPolicy.Access.ADMIN);
        assertThat(policy.access(HttpMethod.POST, "/api/v1/pfm/boxes/list"))
                .isEqualTo(GatewayAccessPolicy.Access.USER);
        assertThat(policy.access(HttpMethod.POST, "/api/v1/transfers/create"))
                .isEqualTo(GatewayAccessPolicy.Access.USER);
        assertThat(policy.access(HttpMethod.POST, "/internal/v1/ledger/account/create"))
                .isEqualTo(GatewayAccessPolicy.Access.NOT_GATEWAY_API);
    }

    @Test
    void enforcesRoleAndOnboardingScopeRequirements() {
        GatewayToken user = token(List.of("USER"), List.of());
        GatewayToken admin = token(List.of("ADMIN"), List.of());
        GatewayToken onboarding = token(
                List.of("ONBOARDING"),
                List.of(GatewayAccessPolicy.ACCOUNT_REGISTER_SCOPE)
        );
        GatewayToken referralOnboarding = token(
                List.of("ONBOARDING"),
                List.of(GatewayAccessPolicy.REFERRAL_SET_SCOPE)
        );

        assertThat(policy.permits(GatewayAccessPolicy.Access.USER, user)).isTrue();
        assertThat(policy.permits(GatewayAccessPolicy.Access.USER, admin)).isFalse();
        assertThat(policy.permits(GatewayAccessPolicy.Access.ADMIN, admin)).isTrue();
        assertThat(policy.permits(GatewayAccessPolicy.Access.ONBOARDING_ACCOUNT, onboarding)).isTrue();
        assertThat(policy.permits(GatewayAccessPolicy.Access.ONBOARDING_DEVICE, onboarding)).isFalse();
        assertThat(policy.permits(GatewayAccessPolicy.Access.ONBOARDING_REFERRAL, user)).isFalse();
        assertThat(policy.permits(GatewayAccessPolicy.Access.ONBOARDING_REFERRAL, referralOnboarding)).isTrue();
    }

    private GatewayToken token(List<String> roles, List<String> scopes) {
        return new GatewayToken("user-1", null, null, "token-1", roles, scopes);
    }
}
