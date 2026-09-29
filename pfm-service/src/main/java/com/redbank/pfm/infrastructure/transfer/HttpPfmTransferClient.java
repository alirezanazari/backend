package com.redbank.pfm.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.pfm.application.PfmBusinessRuleException;
import com.redbank.pfm.application.PfmConflictException;
import com.redbank.pfm.application.PfmDuplicateRequestException;
import com.redbank.pfm.application.PfmExternalServiceException;
import com.redbank.pfm.application.PfmFeatureDisabledException;
import com.redbank.pfm.application.PfmInsufficientFundsException;
import com.redbank.pfm.application.PfmNotFoundException;
import com.redbank.pfm.application.PfmUnauthorizedException;
import com.redbank.pfm.application.PfmTransferClient;
import com.redbank.pfm.application.PfmTransferResult;
import com.redbank.pfm.config.TransferClientProperties;
import com.redbank.pfm.infrastructure.http.HttpApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.RequestContext;

@Component
@ConditionalOnProperty(
        prefix = "redbank.transfer-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpPfmTransferClient implements PfmTransferClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Logger log = LoggerFactory.getLogger(HttpPfmTransferClient.class);

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
            String authorizationHeader,
            RequestContext context,
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey,
            String description
    ) {
        try {
            ApiResponse<CreateTransferResponse> response = restClient.post()
                    .uri("/api/v1/transfers/create")
                    .header("Authorization", authorizationHeader)
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(new ApiRequest<>(
                            context,
                            new CreateTransferRequest(
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
                            description
                            )
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
            log.error("Transfer service call failed", exception);

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

        if (error.code() == null) {
            return new PfmExternalServiceException(message, exception);
        }
        return switch (error.code()) {
            case NOT_FOUND -> new PfmNotFoundException(message);
            case UNAUTHORIZED, FORBIDDEN -> new PfmUnauthorizedException(message);
            case INSUFFICIENT_FUNDS -> new PfmInsufficientFundsException(message);
            case FEATURE_DISABLED -> new PfmFeatureDisabledException(message);
            case DUPLICATE_REQUEST -> new PfmDuplicateRequestException(message);
            case CONFLICT -> new PfmConflictException(message);
            case BUSINESS_RULE_VIOLATION, VALIDATION_ERROR -> new PfmBusinessRuleException(message);
            default -> new PfmExternalServiceException(message, exception);
        };
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
