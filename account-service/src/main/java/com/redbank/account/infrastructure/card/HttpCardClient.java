package com.redbank.account.infrastructure.card;

import com.redbank.account.application.AccountExternalServiceException;
import com.redbank.account.application.CardAccountClient;
import com.redbank.account.config.CardClientProperties;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.card.CardForm;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.event.EventHeaders;
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
    public CardSummary createDefaultCard(
            UUID eventId,
            String accountNumber,
            UUID userId,
            UUID profileId,
            CardForm form
    ) {
        try {
            ApiResponse<CardSummary> response = restClient.post()
                    .uri("/internal/v1/card/default/create")
                    .header(EventHeaders.EVENT_ID, eventId.toString())
                    .header(EventHeaders.EVENT_TYPE, "DEFAULT_CARD_CREATE_REQUESTED")
                    .body(new CreateDefaultCardRequest(
                            accountNumber,
                            userId.toString(),
                            profileId.toString(),
                            form
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

    @Override
    public boolean close(UUID eventId, String accountNumber) {
        return changeAccountStatus(
                "/internal/v1/card/account/close",
                eventId,
                "CARD_ACCOUNT_CLOSE_REQUESTED",
                accountNumber,
                "INACTIVE"
        );
    }

    @Override
    public boolean reopen(UUID eventId, String accountNumber) {
        return changeAccountStatus(
                "/internal/v1/card/account/reopen",
                eventId,
                "CARD_ACCOUNT_REOPEN_REQUESTED",
                accountNumber,
                "ACTIVE"
        );
    }

    private boolean changeAccountStatus(
            String path,
            UUID eventId,
            String eventType,
            String accountNumber,
            String expectedStatus
    ) {
        try {
            ApiResponse<CardAccountLifecycleResponse> response = restClient.post()
                    .uri(path)
                    .header(EventHeaders.EVENT_ID, eventId.toString())
                    .header(EventHeaders.EVENT_TYPE, eventType)
                    .body(new CardAccountLifecycleRequest(accountNumber))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null
                    && response.data() != null
                    && expectedStatus.equals(response.data().status());
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Card service call failed", exception);
        }
    }

    private record CreateDefaultCardRequest(
            String accountNumber,
            String userId,
            String profileId,
            CardForm form
    ) {
    }

    private record CardAccountLifecycleRequest(String accountNumber) {
    }

    private record CardAccountLifecycleResponse(String accountNumber, String status, Integer updatedCards) {
    }
}
