package com.redbank.kyc.infrastructure.account;

import com.redbank.kyc.application.ActiveReferrerAccount;
import com.redbank.kyc.application.ReferrerAccountClient;
import com.redbank.kyc.application.ResolvedReferrer;
import com.redbank.kyc.config.ReferrerAccountClientProperties;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "redbank.referrer-account-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpReferrerAccountClient implements ReferrerAccountClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpReferrerAccountClient(ReferrerAccountClientProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public ResolvedReferrer resolveByCode(String referralCode) {
        // TODO
        throw new UnsupportedOperationException("Implement referral code resolution");
    }

    @Override
    public ActiveReferrerAccount findActiveAccount(UUID referrerUserId) {
        // TODO
        throw new UnsupportedOperationException("Implement active referrer account lookup");
    }
}
