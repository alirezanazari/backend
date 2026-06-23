package com.redbank.pfm.infrastructure.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.pfm.application.PfmBusinessRuleException;
import com.redbank.pfm.application.PfmExternalServiceException;
import com.redbank.pfm.application.PfmNotFoundException;
import com.redbank.pfm.application.VaultAccountClient;
import com.redbank.pfm.config.AccountClientProperties;
import com.redbank.pfm.infrastructure.http.HttpApiError;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.account-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpVaultAccountClient implements VaultAccountClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpVaultAccountClient(
            RestClient.Builder restClientBuilder,
            AccountClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.vaultCreateServiceToken())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountSummary create(UUID userId, UUID profileId, String currency) {
        try {
            ApiResponse<CreateVaultAccountResponse> response = restClient.post()
                    .uri("/internal/v1/account/vault/create")
                    .body(new CreateVaultAccountRequest(
                            userId.toString(),
                            profileId.toString(),
                            currency
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            AccountSummary account = response == null || response.data() == null
                    ? null
                    : response.data().account();
            if (account == null) {
                throw new PfmExternalServiceException("Account service returned an invalid vault");
            }
            return account;
        } catch (RestClientResponseException exception) {
            throw downstreamFailure(exception);
        } catch (RestClientException exception) {
            throw new PfmExternalServiceException("Account service call failed", exception);
        }
    }

    private RuntimeException downstreamFailure(RestClientResponseException exception) {
        HttpApiError error = HttpApiError.from(objectMapper, exception);
        String message = defaultMessage(error.message(), "Account service rejected vault creation");
        if (error.code() == StatusCode.NOT_FOUND) {
            return new PfmNotFoundException(message);
        }
        if (error.code() == StatusCode.BUSINESS_RULE_VIOLATION
                || error.code() == StatusCode.CONFLICT
                || error.code() == StatusCode.VALIDATION_ERROR) {
            return new PfmBusinessRuleException(message);
        }
        return new PfmExternalServiceException(message, exception);
    }

    private String defaultMessage(String message, String defaultValue) {
        return message == null || message.isBlank() ? defaultValue : message;
    }

    private record CreateVaultAccountRequest(
            String userId,
            String profileId,
            String currency
    ) {
    }

    private record CreateVaultAccountResponse(
            AccountSummary account
    ) {
    }
}
