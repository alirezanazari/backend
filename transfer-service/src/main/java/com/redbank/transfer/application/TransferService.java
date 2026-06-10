package com.redbank.transfer.application;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferDestinationSummary;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.contracts.transfer.TransferStatus;
import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import com.redbank.transfer.api.v1.dto.CreateTransferResponse;
import com.redbank.transfer.api.v1.dto.ListRecentTransfersRequest;
import com.redbank.transfer.api.v1.dto.ListRecentTransfersResponse;
import com.redbank.transfer.api.v1.dto.ListTransferMethodsRequest;
import com.redbank.transfer.api.v1.dto.ListTransferMethodsResponse;
import com.redbank.transfer.api.v1.dto.RecentTransferDestination;
import com.redbank.transfer.api.v1.dto.TransferMethodSummary;
import com.redbank.transfer.config.TransferProperties;
import com.redbank.transfer.domain.IdempotencyRecord;
import com.redbank.transfer.domain.TransferMethodRecord;
import com.redbank.transfer.domain.TransferRecord;
import com.redbank.transfer.domain.TransferStatusHistoryRecord;
import com.redbank.transfer.infrastructure.IdempotencyRepository;
import com.redbank.transfer.infrastructure.TransferMethodRepository;
import com.redbank.transfer.infrastructure.TransferRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_RECENT_CANDIDATES = 500;

    private final TransferMethodRepository transferMethodRepository;
    private final TransferRepository transferRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final JwtTokenService jwtTokenService;
    private final FeatureFlagClient featureFlagClient;
    private final TransferPartyClient transferPartyClient;
    private final LedgerTransferClient ledgerTransferClient;
    private final TransferProperties transferProperties;
    private final Clock clock;

    public TransferService(
            TransferMethodRepository transferMethodRepository,
            TransferRepository transferRepository,
            IdempotencyRepository idempotencyRepository,
            JwtTokenService jwtTokenService,
            FeatureFlagClient featureFlagClient,
            TransferPartyClient transferPartyClient,
            LedgerTransferClient ledgerTransferClient,
            TransferProperties transferProperties,
            Clock clock
    ) {
        this.transferMethodRepository = transferMethodRepository;
        this.transferRepository = transferRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jwtTokenService = jwtTokenService;
        this.featureFlagClient = featureFlagClient;
        this.transferPartyClient = transferPartyClient;
        this.ledgerTransferClient = ledgerTransferClient;
        this.transferProperties = transferProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListTransferMethodsResponse listMethods(
            String authorizationHeader,
            RequestContext context,
            ListTransferMethodsRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        resolveProfileId(token, context);
        ListTransferMethodsRequest filters = request == null
                ? new ListTransferMethodsRequest(null, null)
                : request;
        List<TransferMethodSummary> methods = transferMethodRepository.find(
                        filters.sourceType(),
                        filters.destinationType()
                ).stream()
                .map(method -> new TransferMethodSummary(
                        method.method(),
                        method.enabled() && featureFlagClient.isEnabled(flagKey(method.method())),
                        method.title()
                ))
                .toList();
        return new ListTransferMethodsResponse(methods);
    }

    @Transactional(
            noRollbackFor = {
                    TransferInsufficientFundsException.class,
                    TransferExternalServiceException.class
            }
    )
    public CreateTransferResponse create(
            String authorizationHeader,
            String idempotencyHeader,
            String requestId,
            RequestContext context,
            CreateTransferRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);
        requireVerifiedDevice(token);
        CreateTransferRequest normalized = normalize(request, idempotencyHeader);
        validateMethodShape(normalized);

        String requestHash = requestHash(normalized);
        Instant now = clock.instant();
        IdempotencyRecord idempotency = idempotencyRepository.claim(
                token.userId(),
                normalized.idempotencyKey(),
                requestHash,
                now.plus(transferProperties.idempotencyTtlHours(), ChronoUnit.HOURS),
                now
        );
        if (!idempotency.requestHash().equals(requestHash)) {
            throw new TransferDuplicateRequestException(
                    "Idempotency key was already used with a different request"
            );
        }
        if (idempotency.transferId() != null) {
            return replay(token.userId(), idempotency.transferId());
        }

        TransferMethodRecord method = transferMethodRepository.findByMethod(normalized.method())
                .orElseThrow(() -> new TransferNotFoundException("Transfer method was not found"));
        if (!method.enabled() || !featureFlagClient.isEnabled(flagKey(method.method()))) {
            throw new TransferFeatureDisabledException("Transfer method is disabled");
        }

        ResolvedTransferParties parties = transferPartyClient.resolve(normalized, token.userId(), profileId);
        validateResolvedParties(normalized, parties);
        if (parties.sourceAccountNumber().equals(parties.destinationAccountNumber())) {
            throw new TransferBusinessRuleException("Source and destination accounts must be different");
        }

        TransferRecord transfer = new TransferRecord(
                UUID.randomUUID(),
                requestId,
                token.userId(),
                profileId,
                normalized.sourceType(),
                parties.sourceReference(),
                parties.sourceAccountNumber(),
                normalized.destinationType(),
                parties.destinationReference(),
                parties.destinationAccountNumber(),
                parties.destination().destinationName(),
                destinationSnapshot(parties.destination()),
                normalized.amount().amount(),
                normalized.amount().currency(),
                normalized.method(),
                TransferStatus.PENDING,
                null,
                normalized.description(),
                null,
                now,
                now
        );
        transferRepository.save(transfer);
        transferRepository.saveStatusHistory(history(transfer.id(), null, TransferStatus.PENDING, null, now));
        idempotencyRepository.attachTransfer(idempotency.id(), transfer.id(), now);

        Instant processingAt = clock.instant();
        changeStatus(transfer, TransferStatus.PROCESSING, null, null, processingAt);
        transfer = transfer.withStatus(TransferStatus.PROCESSING, null, null, processingAt);

        try {
            LedgerTransferResult ledgerResult = ledgerTransferClient.post(
                    transfer.id().toString(),
                    transfer.sourceAccountNumber(),
                    transfer.destinationAccountNumber(),
                    new Money(transfer.amount(), transfer.currency()),
                    normalized.idempotencyKey()
            );
            if (ledgerResult == null
                    || ledgerResult.ledgerTransactionId() == null
                    || ledgerResult.ledgerTransactionId().isBlank()) {
                throw new TransferExternalServiceException("Ledger returned an invalid transfer result");
            }
            Instant succeededAt = clock.instant();
            changeStatus(
                    transfer,
                    TransferStatus.SUCCESS,
                    ledgerResult.ledgerTransactionId(),
                    null,
                    succeededAt
            );
            TransferRecord succeeded = transfer.withStatus(
                    TransferStatus.SUCCESS,
                    ledgerResult.ledgerTransactionId(),
                    null,
                    succeededAt
            );
            idempotencyRepository.complete(
                    idempotency.id(),
                    responseSnapshot(succeeded),
                    succeededAt
            );
            return toResponse(succeeded);
        } catch (TransferInsufficientFundsException exception) {
            fail(transfer, idempotency.id(), "INSUFFICIENT_FUNDS");
            throw exception;
        } catch (TransferExternalServiceException exception) {
            fail(transfer, idempotency.id(), "EXTERNAL_SERVICE_ERROR");
            throw exception;
        } catch (RuntimeException exception) {
            fail(transfer, idempotency.id(), "EXTERNAL_SERVICE_ERROR");
            throw new TransferExternalServiceException("Ledger transfer failed", exception);
        }
    }

    @Transactional(readOnly = true)
    public ListRecentTransfersResponse listRecent(
            String authorizationHeader,
            RequestContext context,
            ListRecentTransfersRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);
        int limit = request == null || request.limit() == null ? DEFAULT_RECENT_LIMIT : request.limit();
        int candidateLimit = Math.min(MAX_RECENT_CANDIDATES, limit * 10);
        Map<String, RecentTransferDestination> destinations = new LinkedHashMap<>();
        for (TransferRecord transfer : transferRepository.findSuccessfulRecent(
                token.userId(),
                profileId,
                candidateLimit
        )) {
            destinations.putIfAbsent(
                    transfer.destinationAccountOrCardNumber(),
                    new RecentTransferDestination(
                            transfer.destinationDisplayName(),
                            transfer.destinationAccountOrCardNumber()
                    )
            );
            if (destinations.size() == limit) {
                break;
            }
        }
        return new ListRecentTransfersResponse(List.copyOf(destinations.values()));
    }

    private CreateTransferResponse replay(UUID userId, UUID transferId) {
        TransferRecord transfer = transferRepository.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new TransferConflictException("Idempotent transfer result was not found"));
        if (transfer.status() == TransferStatus.FAILED) {
            replayFailure(transfer.failureReason());
        }
        if (transfer.status() != TransferStatus.SUCCESS) {
            throw new TransferConflictException("Transfer is still processing");
        }
        return toResponse(transfer);
    }

    private void replayFailure(String failureReason) {
        if ("INSUFFICIENT_FUNDS".equals(failureReason)) {
            throw new TransferInsufficientFundsException("Insufficient funds");
        }
        throw new TransferExternalServiceException("Transfer failed in an external service");
    }

    private void fail(TransferRecord transfer, UUID idempotencyId, String reason) {
        Instant failedAt = clock.instant();
        changeStatus(transfer, TransferStatus.FAILED, null, reason, failedAt);
        TransferRecord failed = transfer.withStatus(TransferStatus.FAILED, null, reason, failedAt);
        idempotencyRepository.complete(idempotencyId, responseSnapshot(failed), failedAt);
    }

    private void changeStatus(
            TransferRecord transfer,
            TransferStatus newStatus,
            String ledgerTransactionId,
            String failureReason,
            Instant changedAt
    ) {
        if (!transferRepository.updateStatus(
                transfer.id(),
                transfer.status(),
                newStatus,
                ledgerTransactionId,
                failureReason,
                changedAt
        )) {
            throw new TransferConflictException("Transfer status changed concurrently");
        }
        transferRepository.saveStatusHistory(history(
                transfer.id(),
                transfer.status(),
                newStatus,
                failureReason,
                changedAt
        ));
    }

    private CreateTransferRequest normalize(CreateTransferRequest request, String idempotencyHeader) {
        if (idempotencyHeader == null || idempotencyHeader.isBlank()) {
            throw validation("Idempotency-Key", "header is required", "IDEMPOTENCY_HEADER_REQUIRED");
        }
        String header = idempotencyHeader.trim();
        String body = request.idempotencyKey().trim();
        if (!header.equals(body)) {
            throw validation(
                    "data.idempotencyKey",
                    "must match Idempotency-Key header",
                    "IDEMPOTENCY_KEY_MISMATCH"
            );
        }
        String currency = request.amount().currency().trim().toUpperCase(Locale.ROOT);
        if (request.amount().amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw validation("data.amount.amount", "must be greater than zero", "AMOUNT_POSITIVE");
        }
        return new CreateTransferRequest(
                body,
                request.method(),
                request.sourceType(),
                trimToNull(request.sourceAccountNumber()),
                trimToNull(request.sourceCardNumber()),
                request.destinationType(),
                trimToNull(request.destinationAccountNumber()),
                trimToNull(request.destinationIban()),
                trimToNull(request.destinationCardNumber()),
                new Money(request.amount().amount(), currency),
                trimToNull(request.description())
        );
    }

    private void validateMethodShape(CreateTransferRequest request) {
        TransferMethod expected = switch (request.sourceType()) {
            case ACCOUNT -> request.destinationType() == DestinationType.ACCOUNT
                    ? TransferMethod.ACCOUNT_TO_ACCOUNT
                    : TransferMethod.ACCOUNT_TO_CARD;
            case CARD -> request.destinationType() == DestinationType.ACCOUNT
                    ? TransferMethod.CARD_TO_ACCOUNT
                    : TransferMethod.CARD_TO_CARD;
        };
        if (request.method() != expected) {
            throw validation(
                    "data.method",
                    "does not match sourceType and destinationType",
                    "TRANSFER_METHOD_MISMATCH"
            );
        }
        if (request.sourceType() == DestinationType.ACCOUNT) {
            requirePresent(request.sourceAccountNumber(), "data.sourceAccountNumber");
            requireAbsent(request.sourceCardNumber(), "data.sourceCardNumber");
        } else {
            requirePresent(request.sourceCardNumber(), "data.sourceCardNumber");
            requireAbsent(request.sourceAccountNumber(), "data.sourceAccountNumber");
        }
        if (request.destinationType() == DestinationType.CARD) {
            requirePresent(request.destinationCardNumber(), "data.destinationCardNumber");
            requireAbsent(request.destinationAccountNumber(), "data.destinationAccountNumber");
            requireAbsent(request.destinationIban(), "data.destinationIban");
            return;
        }
        requireAbsent(request.destinationCardNumber(), "data.destinationCardNumber");
        boolean hasAccountNumber = request.destinationAccountNumber() != null;
        boolean hasIban = request.destinationIban() != null;
        if (hasAccountNumber == hasIban) {
            throw validation(
                    "data.destinationAccountNumber",
                    "provide exactly one destination account number or IBAN",
                    "DESTINATION_ACCOUNT_REFERENCE"
            );
        }
    }

    private void validateResolvedParties(CreateTransferRequest request, ResolvedTransferParties parties) {
        if (parties == null
                || parties.destination() == null
                || parties.destination().destinationType() != request.destinationType()
                || !isAccountNumber(parties.sourceAccountNumber())
                || !isAccountNumber(parties.destinationAccountNumber())
                || parties.sourceReference() == null
                || parties.sourceReference().isBlank()
                || parties.destinationReference() == null
                || parties.destinationReference().isBlank()
                || parties.destination().destinationName() == null
                || parties.destination().destinationName().isBlank()
                || destinationSnapshot(parties.destination()) == null
                || destinationSnapshot(parties.destination()).isBlank()) {
            throw new TransferExternalServiceException("Party validation returned an invalid result");
        }
    }

    private UUID resolveProfileId(VerifiedToken token, RequestContext context) {
        String contextProfileId = context.account() == null ? null : context.account().profileId();
        UUID profileId = contextProfileId == null || contextProfileId.isBlank()
                ? token.profileId()
                : parseProfileId(contextProfileId);
        if (profileId == null) {
            throw new TransferBusinessRuleException("Active profile is required");
        }
        if (token.profileId() != null && !token.profileId().equals(profileId)) {
            throw new TransferUnauthorizedException("Unauthorized");
        }
        return profileId;
    }

    private void requireVerifiedDevice(VerifiedToken token) {
        if (token.deviceId() == null) {
            throw new TransferBusinessRuleException("Verified device is required");
        }
    }

    private UUID parseProfileId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw validation("context.account.profileId", "must be a UUID", "PROFILE_ID_FORMAT");
        }
    }

    private String flagKey(TransferMethod method) {
        return "transfer." + method.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".enabled";
    }

    private String destinationSnapshot(TransferDestinationSummary destination) {
        return destination.destinationType() == DestinationType.ACCOUNT
                ? destination.accountNumber()
                : destination.maskedCardNumber();
    }

    private CreateTransferResponse toResponse(TransferRecord transfer) {
        TransferDestinationSummary destination = new TransferDestinationSummary(
                transfer.destinationType(),
                transfer.destinationDisplayName(),
                null,
                transfer.destinationType() == DestinationType.ACCOUNT
                        ? transfer.destinationAccountOrCardNumber()
                        : null,
                null,
                transfer.destinationType() == DestinationType.CARD
                        ? transfer.destinationAccountOrCardNumber()
                        : null
        );
        return new CreateTransferResponse(
                transfer.id().toString(),
                transfer.status(),
                transfer.ledgerTransactionId(),
                destination,
                new Money(transfer.amount(), transfer.currency()),
                transfer.createdAt()
        );
    }

    private TransferStatusHistoryRecord history(
            UUID transferId,
            TransferStatus previousStatus,
            TransferStatus newStatus,
            String reason,
            Instant createdAt
    ) {
        return new TransferStatusHistoryRecord(
                UUID.randomUUID(),
                transferId,
                previousStatus,
                newStatus,
                reason,
                createdAt
        );
    }

    private String responseSnapshot(TransferRecord transfer) {
        return String.join(
                "|",
                transfer.id().toString(),
                transfer.status().name(),
                Objects.toString(transfer.ledgerTransactionId(), ""),
                Objects.toString(transfer.failureReason(), "")
        );
    }

    private String requestHash(CreateTransferRequest request) {
        String canonical = String.join(
                "\u001f",
                request.method().name(),
                request.sourceType().name(),
                Objects.toString(request.sourceAccountNumber(), ""),
                Objects.toString(request.sourceCardNumber(), ""),
                request.destinationType().name(),
                Objects.toString(request.destinationAccountNumber(), ""),
                Objects.toString(request.destinationIban(), ""),
                Objects.toString(request.destinationCardNumber(), ""),
                request.amount().amount().stripTrailingZeros().toPlainString(),
                request.amount().currency(),
                Objects.toString(request.description(), "")
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isAccountNumber(String value) {
        return value != null && value.matches("\\d{18}");
    }

    private void requirePresent(String value, String field) {
        if (value == null) {
            throw validation(field, "is required", "FIELD_REQUIRED");
        }
    }

    private void requireAbsent(String value, String field) {
        if (value != null) {
            throw validation(field, "must not be provided", "FIELD_NOT_ALLOWED");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TransferValidationException validation(String field, String message, String code) {
        return new TransferValidationException(List.of(new StatusDetail(field, message, code)));
    }
}
