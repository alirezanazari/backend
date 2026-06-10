package com.redbank.kyc.infrastructure.ledger;

import com.redbank.kyc.application.ReferralRewardClient;
import com.redbank.kyc.application.ReferralRewardCommand;
import com.redbank.kyc.application.ReferralRewardResult;
import com.redbank.kyc.config.ReferralRewardClientProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "redbank.referral-reward-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpReferralRewardClient implements ReferralRewardClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpReferralRewardClient(ReferralRewardClientProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public ReferralRewardResult requestReward(ReferralRewardCommand command) {
        // TODO
        throw new UnsupportedOperationException("Implement referral reward dispatch");
    }
}
