package com.redbank.account.infrastructure.ledger;

import com.redbank.account.application.AccountExternalServiceException;
import com.redbank.account.application.LedgerFundingClient;
import com.redbank.account.application.LedgerFundingResult;
import com.redbank.account.config.LedgerClientProperties;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;


@Component
public class HttpLedgerFundingClient implements LedgerFundingClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private final RestClient restClient;

    public HttpLedgerFundingClient(RestClient.Builder restClientBuilder, LedgerClientProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.accountFundingToken())
                .build();
    }

    @Override
    public LedgerFundingResult fund(String fundingId,
                                    String accountNumber,
                                    Money amount){
        AccountFundingRequest request = new AccountFundingRequest(fundingId, accountNumber, amount);
        ApiResponse<LedgerFundingResult> response;
        try {
            response = restClient.post()
                    .uri("/internal/v1/ledger/transactions/account-funding")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw new AccountExternalServiceException("Ledger funding call failed", exception);
        }

        if (response == null || response.data() == null
                || response.data().ledgerTransactionId() == null
                || response.data().ledgerTransactionId().isBlank()
                || response.data().status() == null
                || response.data().status().isBlank()
                || response.data().createdAt() == null) {
            throw new AccountExternalServiceException("Ledger funding response was empty", null);
        }
        return response.data();
    }

    private record AccountFundingRequest(String fundingId,
                                         String targetExternalAccountNumber,
                                         Money amount) {
    }
}
