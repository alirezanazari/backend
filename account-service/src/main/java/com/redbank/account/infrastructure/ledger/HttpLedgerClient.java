package com.redbank.account.infrastructure.ledger;

import com.redbank.account.application.AccountExternalServiceException;
import com.redbank.account.application.LedgerAccountClient;
import com.redbank.account.application.LedgerBalanceClient;
import com.redbank.account.config.LedgerClientProperties;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.event.EventHeaders;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "redbank.ledger", name = "enabled", havingValue = "true")
public class HttpLedgerClient implements LedgerAccountClient, LedgerBalanceClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpLedgerClient(RestClient.Builder restClientBuilder, LedgerClientProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public boolean createAccount(UUID eventId, String accountNumber, UUID ownerUserId, String currency) {
        CreateLedgerAccountRequest request = new CreateLedgerAccountRequest(
                accountNumber,
                ownerUserId.toString(),
                currency
        );
        ApiResponse<CreateLedgerAccountResponse> response = postEvent(
                "/internal/v1/ledger/account/create",
                eventId,
                "LEDGER_ACCOUNT_CREATE_REQUESTED",
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        return response != null && response.data() != null && "ACTIVE".equals(response.data().status());
    }

    @Override
    public boolean close(UUID eventId, String accountNumber) {
        CloseLedgerAccountRequest request = new CloseLedgerAccountRequest(accountNumber);
        ApiResponse<CloseLedgerAccountResponse> response = postEvent(
                "/internal/v1/ledger/account/close",
                eventId,
                "LEDGER_ACCOUNT_CLOSE_REQUESTED",
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        return response != null && response.data() != null && "CLOSED".equals(response.data().status());
    }

    @Override
    public boolean reopen(UUID eventId, String accountNumber) {
        ReopenLedgerAccountRequest request = new ReopenLedgerAccountRequest(accountNumber);
        ApiResponse<ReopenLedgerAccountResponse> response = postEvent(
                "/internal/v1/ledger/account/reopen",
                eventId,
                "LEDGER_ACCOUNT_REOPEN_REQUESTED",
                request,
                new ParameterizedTypeReference<>() {
                }
        );
        return response != null && response.data() != null && "ACTIVE".equals(response.data().status());
    }

    @Override
    public BalanceSummary getBalance(String accountNumber, String currency) {
        ApiResponse<GetLedgerBalanceResponse> response = post(
                "/internal/v1/ledger/account/balance/get",
                new GetLedgerBalanceRequest(accountNumber),
                new ParameterizedTypeReference<>() {
                }
        );
        if (response == null || response.data() == null || response.data().balance() == null) {
            throw new AccountExternalServiceException("Ledger balance response was empty", null);
        }
        return response.data().balance();
    }

    private <T> ApiResponse<T> post(
            String path,
            Object request,
            ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        try {
            return restClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Ledger service call failed", exception);
        }
    }

    private <T> ApiResponse<T> postEvent(
            String path,
            UUID eventId,
            String eventType,
            Object request,
            ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(EventHeaders.EVENT_ID, eventId.toString())
                    .header(EventHeaders.EVENT_TYPE, eventType)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Ledger service call failed", exception);
        }
    }

    private record CreateLedgerAccountRequest(String externalAccountNumber, String ownerUserId, String currency) {
    }

    private record CreateLedgerAccountResponse(String ledgerAccountId, String status) {
    }

    private record GetLedgerBalanceRequest(String externalAccountNumber) {
    }

    private record GetLedgerBalanceResponse(String externalAccountNumber, String ledgerAccountId, BalanceSummary balance) {
    }

    private record CloseLedgerAccountRequest(String externalAccountNumber) {
    }

    private record CloseLedgerAccountResponse(String ledgerAccountId, String status) {
    }

    private record ReopenLedgerAccountRequest(String externalAccountNumber) {
    }

    private record ReopenLedgerAccountResponse(String ledgerAccountId, String status) {
    }
}
