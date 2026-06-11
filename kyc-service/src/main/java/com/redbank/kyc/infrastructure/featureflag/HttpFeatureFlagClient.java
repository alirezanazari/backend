package com.redbank.kyc.infrastructure.featureflag;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.kyc.application.FeatureFlagClient;
import com.redbank.kyc.application.KycExternalServiceException;
import com.redbank.kyc.config.FeatureFlagClientProperties;
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
    public boolean isEnabled(String key) {
        ApiRequest<ListFlagsRequest> request = new ApiRequest<>(
                new RequestContext("kyc-" + UUID.randomUUID(), null, null, null, null, null),
                new ListFlagsRequest(List.of(key), true)
        );
        try {
            ApiResponse<ListFlagsResponse> response = restClient.post()
                    .uri("/api/v1/feature-flags/list")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<FlagResponse> flags = response == null || response.data() == null
                    ? List.of()
                    : response.data().flags();
            if (flags == null) {
                return false;
            }
            return flags.stream()
                    .filter(flag -> flag != null && key.equals(flag.key()))
                    .findFirst()
                    .map(FlagResponse::enabled)
                    .orElse(false);
        } catch (RestClientException exception) {
            throw new KycExternalServiceException("Feature flag service call failed", exception);
        }
    }

    private record ListFlagsRequest(List<String> keys, Boolean includeDisabled) {
    }

    private record ListFlagsResponse(List<FlagResponse> flags) {
    }

    private record FlagResponse(String key, boolean enabled, String description) {
    }
}
