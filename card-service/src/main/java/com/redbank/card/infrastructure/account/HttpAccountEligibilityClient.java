package com.redbank.card.infrastructure.account;

import com.redbank.card.application.AccountEligibilityClient;
import com.redbank.card.application.CardBusinessRuleException;
import com.redbank.card.application.CardExternalServiceException;
import com.redbank.card.application.EligibleAccount;
import com.redbank.card.config.AccountClientProperties;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.envelope.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "redbank.account-client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpAccountEligibilityClient implements AccountEligibilityClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpAccountEligibilityClient(RestClient.Builder restClientBuilder, AccountClientProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.cardEligibilityGetToken())
                .build();
    }

    @Override
    public EligibleAccount requireEligible(String accountNumber, UUID userId, UUID profileId) {
        try {
            ApiResponse<AccountResponse> response = restClient.post()
                    .uri("/internal/v1/account/card/eligibility/get")
                    .body(new AccountRequest(accountNumber, userId.toString(), profileId.toString()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            AccountResponse data = response == null ? null : response.data();
            if (data == null) {
                throw new CardExternalServiceException("Account service response was empty", null);
            }
            if (!Boolean.TRUE.equals(data.eligible())
                    || data.type() != AccountType.SAVING
                    || data.status() != AccountStatus.ACTIVE) {
                throw new CardBusinessRuleException("Account is not eligible for card ordering");
            }
            return new EligibleAccount(data.accountNumber(), data.currency());
        } catch (CardBusinessRuleException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CardExternalServiceException("Account service call failed", exception);
        }
    }

    private record AccountRequest(String accountNumber, String userId, String profileId) {
    }

    private record AccountResponse(
            String accountNumber,
            String userId,
            String profileId,
            AccountType type,
            AccountStatus status,
            String currency,
            Boolean eligible
    ) {
    }
}
