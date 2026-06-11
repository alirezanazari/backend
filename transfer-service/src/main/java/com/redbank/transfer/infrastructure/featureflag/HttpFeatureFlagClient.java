package com.redbank.transfer.infrastructure.featureflag;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.transfer.application.FeatureFlagClient;
import com.redbank.transfer.application.FeatureFlagSubject;
import com.redbank.transfer.application.TransferExternalServiceException;
import com.redbank.transfer.config.FeatureFlagClientProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        havingValue = "true",
        matchIfMissing = true
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
    public Map<String, Boolean> evaluate(List<String> keys, FeatureFlagSubject subject) {
        ApiRequest<EvaluateFlagsRequest> request = new ApiRequest<>(
                new RequestContext("transfer-" + UUID.randomUUID(), null, null, null, null, null),
                new EvaluateFlagsRequest(
                        keys,
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
            Map<String, Boolean> values = new LinkedHashMap<>();
            keys.forEach(key -> values.put(key, false));
            if (flags != null) {
                flags.stream()
                        .filter(flag -> flag != null && values.containsKey(flag.key()))
                        .forEach(flag -> values.put(flag.key(), flag.enabled()));
            }
            return Map.copyOf(values);
        } catch (RestClientException exception) {
            throw new TransferExternalServiceException("Feature flag service call failed", exception);
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
