package com.redbank.pfm.infrastructure.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.PageRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.pfm.application.LedgerPfmClient;
import com.redbank.pfm.application.PfmBusinessRuleException;
import com.redbank.pfm.application.PfmExternalServiceException;
import com.redbank.pfm.application.PfmNotFoundException;
import com.redbank.pfm.config.LedgerClientProperties;
import com.redbank.pfm.infrastructure.http.HttpApiError;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.ledger-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpLedgerPfmClient implements LedgerPfmClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpLedgerPfmClient(
            RestClient.Builder restClientBuilder,
            LedgerClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void createAccount(String accountNumber, UUID userId, String currency) {
        try {
            ApiResponse<CreateLedgerAccountResponse> response = restClient.post()
                    .uri("/internal/v1/ledger/account/create")
                    .body(new CreateLedgerAccountRequest(accountNumber, userId.toString(), currency))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            CreateLedgerAccountResponse result = response == null ? null : response.data();
            if (result == null
                    || result.ledgerAccountId() == null
                    || result.ledgerAccountId().isBlank()
                    || !"ACTIVE".equals(result.status())) {
                throw new PfmExternalServiceException("Ledger returned an invalid account result");
            }
        } catch (RestClientResponseException exception) {
            throw downstreamFailure("Ledger rejected account creation", exception);
        } catch (RestClientException exception) {
            throw new PfmExternalServiceException("Ledger service call failed", exception);
        }
    }

    @Override
    public BalanceSummary getBalance(String accountNumber) {
        try {
            ApiResponse<GetLedgerBalanceResponse> response = restClient.post()
                    .uri("/internal/v1/ledger/account/balance/get")
                    .body(new GetLedgerBalanceRequest(accountNumber))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            BalanceSummary balance = response == null || response.data() == null
                    ? null
                    : response.data().balance();
            if (balance == null) {
                throw new PfmExternalServiceException("Ledger returned an invalid balance");
            }
            return balance;
        } catch (RestClientResponseException exception) {
            throw downstreamFailure("Ledger rejected balance lookup", exception);
        } catch (RestClientException exception) {
            throw new PfmExternalServiceException("Ledger service call failed", exception);
        }
    }

    @Override
    public LedgerEntries listEntries(
            String accountNumber,
            LocalDate fromDate,
            LocalDate toDate,
            PageRequest page
    ) {
        try {
            ApiResponse<LedgerEntries> response = restClient.post()
                    .uri("/internal/v1/ledger/account/entries/list")
                    .body(new ListLedgerEntriesRequest(accountNumber, fromDate, toDate, page))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            LedgerEntries entries = response == null ? null : response.data();
            if (entries == null || entries.entries() == null || entries.page() == null) {
                throw new PfmExternalServiceException("Ledger returned an invalid entry list");
            }
            return entries;
        } catch (RestClientResponseException exception) {
            throw downstreamFailure("Ledger rejected entry lookup", exception);
        } catch (RestClientException exception) {
            throw new PfmExternalServiceException("Ledger service call failed", exception);
        }
    }

    private RuntimeException downstreamFailure(
            String defaultMessage,
            RestClientResponseException exception
    ) {
        HttpApiError error = HttpApiError.from(objectMapper, exception);
        String message = error.message() == null || error.message().isBlank()
                ? defaultMessage
                : error.message();
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

    private record CreateLedgerAccountRequest(
            String externalAccountNumber,
            String ownerUserId,
            String currency
    ) {
    }

    private record CreateLedgerAccountResponse(
            String ledgerAccountId,
            String status
    ) {
    }

    private record GetLedgerBalanceRequest(
            String externalAccountNumber
    ) {
    }

    private record GetLedgerBalanceResponse(
            String externalAccountNumber,
            String ledgerAccountId,
            BalanceSummary balance
    ) {
    }

    private record ListLedgerEntriesRequest(
            String externalAccountNumber,
            LocalDate fromDate,
            LocalDate toDate,
            PageRequest page
    ) {
    }
}
