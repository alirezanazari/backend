package com.redbank.card.infrastructure.featureflag;

import com.redbank.card.application.CardExternalServiceException;
import com.redbank.card.application.FeatureFlagClient;
import com.redbank.card.application.FeatureFlagSubject;
import com.redbank.card.config.FeatureFlagClientProperties;
import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.RequestContext;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.feature-flag-client",
        name = "enabled",
        havingValue = "true"
)
public class HttpFeatureFlagClient implements FeatureFlagClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpFeatureFlagClient(
            RestClient.Builder restClientBuilder,
            FeatureFlagClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public boolean isEnabled(String key, FeatureFlagSubject subject) {
        ApiRequest<EvaluateFlagsRequest> request = new ApiRequest<>(
                new RequestContext("card-" + UUID.randomUUID(), null, null, null, null, null),
                new EvaluateFlagsRequest(
                        List.of(key),
                        subject.userId().toString(),
                        subject.profileId() == null ? null : subject.profileId().toString(),
                        subject.accountNumber()
                )
        );
        try {
            ApiResponse<EvaluateFlagsResponse> response = restClient.post()
                    .uri("/api/v1/feature-flags/evaluate")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<EvaluatedFlag> flags = response == null || response.data() == null
                    ? List.of()
                    : response.data().flags();
            if (flags == null) {
                return false;
            }
            return flags.stream()
                    .filter(flag -> flag != null && key.equals(flag.key()))
                    .findFirst()
                    .map(EvaluatedFlag::enabled)
                    .orElse(false);
        } catch (RestClientException exception) {
            throw new CardExternalServiceException("Feature flag service call failed", exception);
        }
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

    private record EvaluatedFlag(String key, boolean enabled) {
    }
}
