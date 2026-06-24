package com.redbank.transfer.infrastructure.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.transfer.application.LedgerTransferClient;
import com.redbank.transfer.application.LedgerTransferResult;
import com.redbank.transfer.application.TransferExternalServiceException;
import com.redbank.transfer.application.TransferInsufficientFundsException;
import com.redbank.transfer.config.LedgerClientProperties;
import com.redbank.transfer.infrastructure.http.HttpApiError;
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
public class HttpLedgerTransferClient implements LedgerTransferClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpLedgerTransferClient(
            RestClient.Builder restClientBuilder,
            LedgerClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.internalTransferToken())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public LedgerTransferResult post(
            String transferId,
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    ) {
        InternalTransferRequest request = new InternalTransferRequest(
                transferId,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                idempotencyKey
        );
        try {
            ApiResponse<LedgerTransferResult> response = restClient.post()
                    .uri("/internal/v1/ledger/transactions/internal-transfer")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            LedgerTransferResult result = response == null ? null : response.data();
            if (result == null
                    || result.ledgerTransactionId() == null
                    || result.ledgerTransactionId().isBlank()
                    || !"POSTED".equals(result.status())) {
                throw new TransferExternalServiceException("Ledger returned an invalid transfer result");
            }
            return result;
        } catch (RestClientResponseException exception) {
            HttpApiError error = HttpApiError.from(objectMapper, exception);
            if (error.code() == StatusCode.INSUFFICIENT_FUNDS) {
                throw new TransferInsufficientFundsException(defaultMessage(error.message(), "Insufficient funds"));
            }
            throw new TransferExternalServiceException(
                    defaultMessage(error.message(), "Ledger service rejected transfer"),
                    exception
            );
        } catch (RestClientException exception) {
            throw new TransferExternalServiceException("Ledger service call failed", exception);
        }
    }

    private String defaultMessage(String message, String defaultValue) {
        return message == null || message.isBlank() ? defaultValue : message;
    }

    private record InternalTransferRequest(
            String referenceId,
            String sourceExternalAccountNumber,
            String destinationExternalAccountNumber,
            Money amount,
            String idempotencyKey
    ) {
    }
}
