package com.redbank.pfm.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.pfm.application.PfmBusinessRuleException;
import com.redbank.pfm.application.PfmExternalServiceException;
import com.redbank.pfm.application.PfmNotFoundException;
import com.redbank.pfm.application.PfmTransferClient;
import com.redbank.pfm.application.PfmTransferResult;
import com.redbank.pfm.config.TransferClientProperties;
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
        prefix = "redbank.transfer-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpPfmTransferClient implements PfmTransferClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpPfmTransferClient(
            RestClient.Builder restClientBuilder,
            TransferClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();

        this.objectMapper = objectMapper;
    }

    @Override
    public PfmTransferResult transfer(
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    ) {
        try {
            ApiResponse<CreateTransferResponse> response = restClient.post()
                    .uri("/api/v1/transfers/create")
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(new CreateTransferRequest(
                            idempotencyKey,
                            TransferMethod.ACCOUNT_TO_ACCOUNT,
                            DestinationType.ACCOUNT,
                            sourceAccountNumber,
                            null,
                            DestinationType.ACCOUNT,
                            destinationAccountNumber,
                            null,
                            null,
                            amount,
                            null
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            CreateTransferResponse result =
                    response == null ? null : response.data();

            if (result == null || result.transferId() == null) {
                throw new PfmExternalServiceException(
                        "Transfer service returned an invalid result"
                );
            }

            return new PfmTransferResult(
                    result.transferId(),
                    result.status(),
                    result.ledgerTransactionId()
            );

        } catch (RestClientResponseException exception) {
            throw downstreamFailure(exception);
        } catch (RestClientException exception) {
            throw new PfmExternalServiceException(
                    "Transfer service call failed",
                    exception
            );
        }
    }

    private RuntimeException downstreamFailure(
            RestClientResponseException exception
    ) {
        HttpApiError error = HttpApiError.from(objectMapper, exception);

        String message = error.message() == null || error.message().isBlank()
                ? "Transfer service rejected transfer"
                : error.message();

        if (error.code() == com.redbank.contracts.envelope.StatusCode.NOT_FOUND) {
            return new PfmNotFoundException(message);
        }

        if (error.code() == com.redbank.contracts.envelope.StatusCode.BUSINESS_RULE_VIOLATION
                || error.code() == com.redbank.contracts.envelope.StatusCode.CONFLICT
                || error.code() == com.redbank.contracts.envelope.StatusCode.VALIDATION_ERROR) {
            return new PfmBusinessRuleException(message);
        }

        return new PfmExternalServiceException(message, exception);
    }

    private record CreateTransferRequest(
            String idempotencyKey,
            TransferMethod method,
            DestinationType sourceType,
            String sourceAccountNumber,
            String sourceCardNumber,
            DestinationType destinationType,
            String destinationAccountNumber,
            String destinationIban,
            String destinationCardNumber,
            Money amount,
            String description
    ) {
    }

    private record CreateTransferResponse(
            String transferId,
            com.redbank.contracts.transfer.TransferStatus status,
            String ledgerTransactionId
    ) {
    }
}
