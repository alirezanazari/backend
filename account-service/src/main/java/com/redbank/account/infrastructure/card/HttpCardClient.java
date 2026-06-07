package com.redbank.account.infrastructure.card;

import com.redbank.account.application.AccountExternalServiceException;
import com.redbank.account.application.CardAccountClient;
import com.redbank.account.config.CardClientProperties;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.envelope.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "redbank.card", name = "enabled", havingValue = "true")
public class HttpCardClient implements CardAccountClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpCardClient(CardClientProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public CardSummary createDefaultCard(String accountNumber, UUID userId, UUID profileId) {
        try {
            ApiResponse<CardSummary> response = restClient.post()
                    .uri("/internal/v1/cards/default/create")
                    .body(new CreateDefaultCardRequest(
                            accountNumber,
                            userId.toString(),
                            profileId.toString()
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.data() == null) {
                throw new AccountExternalServiceException("Card service response was empty", null);
            }
            return response.data();
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Card service call failed", exception);
        }
    }

    private record CreateDefaultCardRequest(String accountNumber, String userId, String profileId) {
    }
}
