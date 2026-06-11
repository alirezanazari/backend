package com.redbank.kyc.infrastructure.ledger;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.kyc.application.KycExternalServiceException;
import com.redbank.kyc.application.ReferralRewardClient;
import com.redbank.kyc.application.ReferralRewardCommand;
import com.redbank.kyc.application.ReferralRewardResult;
import com.redbank.kyc.config.ReferralRewardClientProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    public HttpReferralRewardClient(
            RestClient.Builder restClientBuilder,
            ReferralRewardClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public ReferralRewardResult requestReward(ReferralRewardCommand command) {
        try {
            ApiResponse<ReferralRewardResult> response = restClient.post()
                    .uri("/internal/v1/ledger/transactions/referral-reward")
                    .body(command)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.data() == null) {
                throw new KycExternalServiceException("Ledger service response was empty", null);
            }
            return response.data();
        } catch (RestClientException exception) {
            throw new KycExternalServiceException("Ledger service call failed", exception);
        }
    }
}
