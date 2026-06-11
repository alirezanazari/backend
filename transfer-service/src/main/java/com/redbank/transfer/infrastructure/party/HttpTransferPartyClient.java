package com.redbank.transfer.infrastructure.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Image;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferDestinationSummary;
import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import com.redbank.transfer.application.ResolvedTransferParties;
import com.redbank.transfer.application.TransferBusinessRuleException;
import com.redbank.transfer.application.TransferExternalServiceException;
import com.redbank.transfer.application.TransferNotFoundException;
import com.redbank.transfer.application.TransferPartyClient;
import com.redbank.transfer.config.PartyClientProperties;
import com.redbank.transfer.infrastructure.http.HttpApiError;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.party-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpTransferPartyClient implements TransferPartyClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient accountRestClient;
    private final RestClient cardRestClient;
    private final ObjectMapper objectMapper;

    public HttpTransferPartyClient(
            RestClient.Builder restClientBuilder,
            PartyClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.accountRestClient = client(
                restClientBuilder.clone(),
                properties.accountBaseUrl(),
                properties.serviceToken()
        );
        this.cardRestClient = client(
                restClientBuilder.clone(),
                properties.cardBaseUrl(),
                properties.serviceToken()
        );
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolvedTransferParties resolve(CreateTransferRequest request, UUID userId, UUID profileId) {
        SourceParty source = resolveSource(request, userId, profileId);
        DestinationParty destination = resolveDestination(request);
        requireCurrency(request.amount().currency(), source.account().currency(), "Source");
        requireCurrency(request.amount().currency(), destination.account().currency(), "Destination");
        return new ResolvedTransferParties(
                source.reference(),
                source.account().accountNumber(),
                destination.reference(),
                destination.account().accountNumber(),
                destination.summary()
        );
    }

    private SourceParty resolveSource(CreateTransferRequest request, UUID userId, UUID profileId) {
        if (request.sourceType() == DestinationType.ACCOUNT) {
            AccountParty account = resolveAccount(
                    request.sourceAccountNumber(),
                    null,
                    userId,
                    profileId,
                    true
            );
            return new SourceParty(account.accountId(), account);
        }

        CardParty card = resolveCard(request.sourceCardNumber(), userId, profileId, true);
        AccountParty account = resolveAccount(card.accountNumber(), null, userId, profileId, true);
        return new SourceParty(card.cardId(), account);
    }

    private DestinationParty resolveDestination(CreateTransferRequest request) {
        if (request.destinationType() == DestinationType.ACCOUNT) {
            AccountParty account = resolveAccount(
                    request.destinationAccountNumber(),
                    request.destinationIban(),
                    null,
                    null,
                    false
            );
            return new DestinationParty(
                    account.accountId(),
                    account,
                    new TransferDestinationSummary(
                            DestinationType.ACCOUNT,
                            account.destinationName(),
                            account.image(),
                            account.accountNumber(),
                            account.iban(),
                            null
                    )
            );
        }

        CardParty card = resolveCard(request.destinationCardNumber(), null, null, false);
        AccountParty account = resolveAccount(card.accountNumber(), null, null, null, false);
        return new DestinationParty(
                card.cardId(),
                account,
                new TransferDestinationSummary(
                        DestinationType.CARD,
                        account.destinationName(),
                        account.image(),
                        null,
                        null,
                        card.maskedCardNumber()
                )
        );
    }

    private AccountParty resolveAccount(
            String accountNumber,
            String iban,
            UUID userId,
            UUID profileId,
            boolean requireOwnership
    ) {
        ResolveAccountRequest request = new ResolveAccountRequest(
                accountNumber,
                iban,
                userId == null ? null : userId.toString(),
                profileId == null ? null : profileId.toString(),
                requireOwnership
        );
        try {
            ApiResponse<AccountParty> response = accountRestClient.post()
                    .uri("/internal/v1/account/transfer/resolve")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.data() == null) {
                throw new TransferExternalServiceException("Account service returned an invalid result");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw downstreamFailure("Account", exception);
        } catch (RestClientException exception) {
            throw new TransferExternalServiceException("Account service call failed", exception);
        }
    }

    private CardParty resolveCard(
            String cardNumber,
            UUID userId,
            UUID profileId,
            boolean requireOwnership
    ) {
        ResolveCardRequest request = new ResolveCardRequest(
                cardNumber,
                userId == null ? null : userId.toString(),
                profileId == null ? null : profileId.toString(),
                requireOwnership
        );
        try {
            ApiResponse<CardParty> response = cardRestClient.post()
                    .uri("/internal/v1/card/transfer/resolve")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.data() == null) {
                throw new TransferExternalServiceException("Card service returned an invalid result");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw downstreamFailure("Card", exception);
        } catch (RestClientException exception) {
            throw new TransferExternalServiceException("Card service call failed", exception);
        }
    }

    private RuntimeException downstreamFailure(String service, RestClientResponseException exception) {
        HttpApiError error = HttpApiError.from(objectMapper, exception);
        String message = defaultMessage(error.message(), service + " service rejected transfer resolution");
        if (error.code() == StatusCode.NOT_FOUND) {
            return new TransferNotFoundException(message);
        }
        if (error.code() == StatusCode.BUSINESS_RULE_VIOLATION
                || error.code() == StatusCode.CONFLICT
                || error.code() == StatusCode.VALIDATION_ERROR) {
            return new TransferBusinessRuleException(message);
        }
        return new TransferExternalServiceException(message, exception);
    }

    private void requireCurrency(String requestedCurrency, String accountCurrency, String party) {
        if (accountCurrency == null || !requestedCurrency.equalsIgnoreCase(accountCurrency)) {
            throw new TransferBusinessRuleException(
                    party + " account currency does not match transfer amount"
            );
        }
    }

    private String defaultMessage(String message, String defaultValue) {
        return message == null || message.isBlank() ? defaultValue : message;
    }

    private RestClient client(RestClient.Builder builder, String baseUrl, String serviceToken) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, serviceToken)
                .build();
    }

    private record ResolveAccountRequest(
            String accountNumber,
            String iban,
            String userId,
            String profileId,
            Boolean requireOwnership
    ) {
    }

    private record ResolveCardRequest(
            String cardNumber,
            String userId,
            String profileId,
            Boolean requireOwnership
    ) {
    }

    private record AccountParty(
            String accountId,
            String userId,
            String profileId,
            String accountNumber,
            String iban,
            String currency,
            String destinationName,
            Image image
    ) {
    }

    private record CardParty(
            String cardId,
            String userId,
            String profileId,
            String accountNumber,
            String maskedCardNumber
    ) {
    }

    private record SourceParty(String reference, AccountParty account) {
    }

    private record DestinationParty(
            String reference,
            AccountParty account,
            TransferDestinationSummary summary
    ) {
    }
}
