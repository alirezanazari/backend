package com.redbank.account.infrastructure.featureflag;

import com.redbank.account.application.AccountExternalServiceException;
import com.redbank.account.application.TopUpFeatureFlagClient;
import com.redbank.account.application.TopUpPolicy;
import com.redbank.account.config.FeatureFlagClientProperties;
import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.RequestContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpTopUpFeatureFlagClient implements TopUpFeatureFlagClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String TOP_UP_FLAG_KEY = "account.top-up.enabled";

    private final RestClient restClient;

    public HttpTopUpFeatureFlagClient(
            RestClient.Builder restClientBuilder,
            FeatureFlagClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public TopUpPolicy getTopUpPolicy(UUID userId, UUID profileId, String accountNumber) {
        ApiRequest<EvaluateFlagsRequest> request = new ApiRequest<>(
                new RequestContext("account-top-up-" + UUID.randomUUID(), null, null, null, null, null),
                new EvaluateFlagsRequest(
                        List.of(TOP_UP_FLAG_KEY),
                        userId.toString(),
                        profileId.toString(),
                        accountNumber
                )
        );

        ApiResponse<EvaluateFlagsResponse> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/feature-flags/evaluate")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Feature flag service call failed", exception);
        }

        if (response == null || response.data() == null || response.data().flags() == null) {
            throw new AccountExternalServiceException("Feature flag response was empty", null);
        }

        EvaluatedFlag topUpFlag = response.data().flags().stream()
                .filter(flag -> flag != null && TOP_UP_FLAG_KEY.equals(flag.key()))
                .findFirst()
                .orElseThrow(() -> new AccountExternalServiceException("Top-up feature flag was missing", null));

        if (topUpFlag.minAmount() == null || topUpFlag.maxAmount() == null
                || topUpFlag.minAmount().signum() <= 0
                || topUpFlag.maxAmount().compareTo(topUpFlag.minAmount()) < 0) {
            throw new AccountExternalServiceException("Top-up feature flag has invalid amount limits", null);
        }

        return new TopUpPolicy(topUpFlag.enabled(), topUpFlag.minAmount(), topUpFlag.maxAmount());
    }

    private record EvaluateFlagsRequest(
            List<String> keys,
            String subjectUserId,
            String profileId,
            String accountNumber
    ) {
    }

    private record EvaluateFlagsResponse(List<EvaluatedFlag> flags) {
    }

    private record EvaluatedFlag(
            String key,
            boolean enabled,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
    }
}
