package com.redbank.transfer.infrastructure.party;

import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import com.redbank.transfer.application.ResolvedTransferParties;
import com.redbank.transfer.application.TransferPartyClient;
import com.redbank.transfer.config.PartyClientProperties;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "redbank.party-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpTransferPartyClient implements TransferPartyClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient accountRestClient;
    private final RestClient cardRestClient;

    public HttpTransferPartyClient(PartyClientProperties properties) {
        this.accountRestClient = client(properties.accountBaseUrl(), properties.serviceToken());
        this.cardRestClient = client(properties.cardBaseUrl(), properties.serviceToken());
    }

    @Override
    public ResolvedTransferParties resolve(CreateTransferRequest request, UUID userId, UUID profileId) {
        // TODO
        throw new UnsupportedOperationException("Implement source ownership and destination resolution");
    }

    private RestClient client(String baseUrl, String serviceToken) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, serviceToken)
                .build();
    }
}
