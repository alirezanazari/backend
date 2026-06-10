package com.redbank.card.infrastructure.featureflag;

import com.redbank.card.application.FeatureFlagClient;
import com.redbank.card.config.FeatureFlagClientProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "redbank.feature-flag-client",
        name = "enabled",
        havingValue = "true"
)
public class HttpFeatureFlagClient implements FeatureFlagClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpFeatureFlagClient(FeatureFlagClientProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public boolean isEnabled(String key) {
        // TODO
        throw new UnsupportedOperationException("Implement card feature flag lookup");
    }
}
