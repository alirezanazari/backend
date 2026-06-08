package com.redbank.card.application;

import com.redbank.card.api.internal.dto.CreateDefaultCardRequest;
import com.redbank.card.api.internal.dto.CardAccountLifecycleResponse;
import com.redbank.card.api.v1.dto.BlockCardRequest;
import com.redbank.card.api.v1.dto.BlockCardResponse;
import com.redbank.card.api.v1.dto.CardStatusResponse;
import com.redbank.card.api.v1.dto.ExpireCardRequest;
import com.redbank.card.api.v1.dto.FreezeCardRequest;
import com.redbank.card.api.v1.dto.ListCardsRequest;
import com.redbank.card.api.v1.dto.ListCardsResponse;
import com.redbank.card.api.v1.dto.OrderCardRequest;
import com.redbank.card.api.v1.dto.OrderCardResponse;
import com.redbank.card.api.v1.dto.UnblockCardRequest;
import com.redbank.card.api.v1.dto.UnfreezeCardRequest;
import com.redbank.card.config.CardProperties;
import com.redbank.card.domain.CardActorType;
import com.redbank.card.domain.CardOrderRecord;
import com.redbank.card.domain.CardOrderStatus;
import com.redbank.card.domain.CardRecord;
import com.redbank.card.domain.CardStatusHistoryRecord;
import com.redbank.card.infrastructure.CardOrderRepository;
import com.redbank.card.infrastructure.CardRepository;
import com.redbank.contracts.card.CardStatus;
import com.redbank.contracts.card.CardForm;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.common.Location;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {
    private static final String DEFAULT_ISSUANCE_PREFIX = "DEFAULT:";

    private final CardRepository cardRepository;
    private final CardOrderRepository cardOrderRepository;
    private final JwtTokenService jwtTokenService;
    private final AccountEligibilityClient accountEligibilityClient;
    private final CardNumberGenerator cardNumberGenerator;
    private final CardProperties cardProperties;
    private final Clock clock;

    public CardService(
            CardRepository cardRepository,
            CardOrderRepository cardOrderRepository,
            JwtTokenService jwtTokenService,
            AccountEligibilityClient accountEligibilityClient,
            CardNumberGenerator cardNumberGenerator,
            CardProperties cardProperties,
            Clock clock
    ) {
        this.cardRepository = cardRepository;
        this.cardOrderRepository = cardOrderRepository;
        this.jwtTokenService = jwtTokenService;
        this.accountEligibilityClient = accountEligibilityClient;
        this.cardNumberGenerator = cardNumberGenerator;
        this.cardProperties = cardProperties;
        this.clock = clock;
    }

    @Transactional
    public CardSummary createDefaultCard(CreateDefaultCardRequest request) {
        UUID userId = parseUuid(request.userId(), "data.userId", "USER_ID_UUID");
        UUID profileId = parseUuid(request.profileId(), "data.profileId", "PROFILE_ID_UUID");
        String issuanceReference = DEFAULT_ISSUANCE_PREFIX + request.accountNumber();

        CardSummary card = cardRepository.findByIssuanceReference(issuanceReference)
                .map(existing -> validateExistingDefault(
                        existing,
                        userId,
                        profileId,
                        request.accountNumber(),
                        request.form()
                ))
                .orElseGet(() -> createCard(
                        userId,
                        profileId,
                        request.accountNumber(),
                        request.form(),
                        issuanceReference,
                        CardActorType.SYSTEM,
                        "account-service",
                        "Default card created"
                ));
        if (request.form() == CardForm.PHYSICAL) {
            ensureInitialPhysicalOrder(card);
        }
        return card;
    }

    @Transactional(readOnly = true)
    public ListCardsResponse listCards(
            String authorizationHeader,
            RequestContext context,
            ListCardsRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);
        List<CardRecord> cards = cardRepository.findByUserIdAndProfileId(
                token.userId(),
                profileId,
                request.statuses(),
                request.accountNumber()
        );

        return new ListCardsResponse(cards.stream()
                .map(this::toSummary)
                .toList());
    }

    @Transactional
    public OrderCardResponse orderCard(
            String authorizationHeader,
            RequestContext context,
            OrderCardRequest request
    ) {
        if (!cardProperties.orderEnabled()) {
            throw new CardFeatureDisabledException("Card ordering is disabled");
        }
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);
        accountEligibilityClient.requireEligible(request.accountNumber(), token.userId(), profileId);

        cardOrderRepository.findOpenByUserIdAndAccountNumber(token.userId(), request.accountNumber())
                .ifPresent(order -> {
                    throw new CardConflictException("An active card order already exists for this account");
                });

        CardRecord digitalCard = cardRepository.findDigitalByUserIdAndProfileIdAndAccountNumber(
                        token.userId(),
                        profileId,
                        request.accountNumber()
                )
                .orElseThrow(() -> new CardBusinessRuleException(
                        "An active digital card is required before ordering a physical card"
                ));

        Instant now = clock.instant();
        UUID orderId = UUID.randomUUID();
        Location delivery = request.deliveryLocation();
        CardOrderRecord order = new CardOrderRecord(
                orderId,
                token.userId(),
                profileId,
                request.accountNumber(),
                delivery == null ? null : delivery.countryCode(),
                delivery == null ? null : delivery.province(),
                delivery == null ? null : delivery.city(),
                delivery == null ? null : delivery.address(),
                delivery == null ? null : delivery.postalCode(),
                request.reason(),
                CardOrderStatus.PENDING,
                digitalCard.id(),
                now,
                now
        );
        try {
            cardOrderRepository.save(order);
        } catch (DataIntegrityViolationException exception) {
            throw new CardConflictException("An active card order already exists for this account");
        }
        if (!cardRepository.updateForm(digitalCard.id(), CardForm.DIGITAL, CardForm.PHYSICAL, now)) {
            throw new CardConflictException("Card form changed concurrently");
        }
        CardRecord physicalCard = digitalCard.withForm(CardForm.PHYSICAL, now);
        return new OrderCardResponse(order.id().toString(), toSummary(physicalCard), order.status().name());
    }

    @Transactional
    public CardAccountLifecycleResponse closeAccount(String accountNumber) {
        int updatedCards = 0;
        for (CardRecord card : cardRepository.findByAccountNumber(accountNumber)) {
            if (card.status() == CardStatus.ACTIVE) {
                changeStatus(
                        card,
                        CardStatus.ACTIVE,
                        CardStatus.INACTIVE,
                        "Linked account closed",
                        CardActorType.SYSTEM,
                        "account-service"
                );
                updatedCards++;
            }
        }
        return new CardAccountLifecycleResponse(accountNumber, CardStatus.INACTIVE.name(), updatedCards);
    }

    @Transactional
    public CardAccountLifecycleResponse reopenAccount(String accountNumber) {
        int updatedCards = 0;
        for (CardRecord card : cardRepository.findByAccountNumber(accountNumber)) {
            if (card.status() == CardStatus.INACTIVE) {
                changeStatus(
                        card,
                        CardStatus.INACTIVE,
                        CardStatus.ACTIVE,
                        "Linked account reopened",
                        CardActorType.SYSTEM,
                        "account-service"
                );
                updatedCards++;
            }
        }
        return new CardAccountLifecycleResponse(accountNumber, CardStatus.ACTIVE.name(), updatedCards);
    }

    @Transactional
    public CardStatusResponse freeze(
            String authorizationHeader,
            RequestContext context,
            FreezeCardRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        CardRecord card = ownedCard(token.userId(), request.cardId());
        validateProfile(card, resolveProfileId(token, context));
        return statusResponse(changeStatus(
                card,
                CardStatus.ACTIVE,
                CardStatus.FROZEN,
                request.reason(),
                CardActorType.USER,
                token.userId().toString()
        ));
    }

    @Transactional
    public CardStatusResponse unfreeze(
            String authorizationHeader,
            RequestContext context,
            UnfreezeCardRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        CardRecord card = ownedCard(token.userId(), request.cardId());
        validateProfile(card, resolveProfileId(token, context));
        return statusResponse(changeStatus(
                card,
                CardStatus.FROZEN,
                CardStatus.ACTIVE,
                "User unfreeze",
                CardActorType.USER,
                token.userId().toString()
        ));
    }

    @Transactional
    public BlockCardResponse block(
            String authorizationHeader,
            RequestContext context,
            BlockCardRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        CardRecord card = ownedCard(token.userId(), request.cardId());
        validateProfile(card, resolveProfileId(token, context));
        if (card.status() == CardStatus.BLOCKED || card.status() == CardStatus.EXPIRED) {
            throw new CardBusinessRuleException("Card cannot be blocked from status " + card.status());
        }
        CardRecord updated = changeStatus(
                card,
                card.status(),
                CardStatus.BLOCKED,
                request.reason(),
                CardActorType.USER,
                token.userId().toString()
        );
        return new BlockCardResponse(
                updated.id().toString(),
                updated.status(),
                Boolean.TRUE.equals(request.reorderRequested()),
                updated.updatedAt()
        );
    }

    @Transactional
    public CardStatusResponse unblock(String authorizationHeader, UnblockCardRequest request) {
        VerifiedToken token = jwtTokenService.verifyAdminToken(authorizationHeader);
        CardRecord card = card(request.cardId());
        CardActorType actorType = token.hasRole("SYSTEM") ? CardActorType.SYSTEM : CardActorType.ADMIN;
        return statusResponse(changeStatus(
                card,
                CardStatus.BLOCKED,
                CardStatus.ACTIVE,
                request.reason(),
                actorType,
                token.userId().toString()
        ));
    }

    @Transactional
    public CardStatusResponse expire(String authorizationHeader, ExpireCardRequest request) {
        VerifiedToken token = jwtTokenService.verifyAdminToken(authorizationHeader);
        CardRecord card = card(request.cardId());
        if (card.status() == CardStatus.EXPIRED) {
            throw new CardBusinessRuleException("Card is already expired");
        }
        CardActorType actorType = token.hasRole("SYSTEM") ? CardActorType.SYSTEM : CardActorType.ADMIN;
        return statusResponse(changeStatus(
                card,
                card.status(),
                CardStatus.EXPIRED,
                defaultReason(request.reason(), "Card expired"),
                actorType,
                token.userId().toString()
        ));
    }

    private CardSummary createCard(
            UUID userId,
            UUID profileId,
            String accountNumber,
            CardForm form,
            String issuanceReference,
            CardActorType actorType,
            String actorId,
            String reason
    ) {
        Instant now = clock.instant();
        CardRecord card = new CardRecord(
                UUID.randomUUID(),
                userId,
                profileId,
                accountNumber,
                cardNumberGenerator.generate().maskedPan(),
                UUID.randomUUID().toString(),
                form,
                CardStatus.ACTIVE,
                YearMonth.from(now.atZone(ZoneOffset.UTC)).plusYears(cardProperties.expiryYears()),
                issuanceReference,
                now,
                now
        );
        try {
            cardRepository.save(card);
            saveHistory(card, null, CardStatus.ACTIVE, reason, actorType, actorId, now);
            return toSummary(card);
        } catch (DataIntegrityViolationException exception) {
            return cardRepository.findByIssuanceReference(issuanceReference)
                    .map(this::toSummary)
                    .orElseThrow(() -> new CardConflictException("Card could not be created"));
        }
    }

    private CardSummary validateExistingDefault(
            CardRecord existing,
            UUID userId,
            UUID profileId,
            String accountNumber,
            CardForm form
    ) {
        if (!existing.userId().equals(userId)
                || !existing.profileId().equals(profileId)
                || !existing.accountNumber().equals(accountNumber)
                || existing.form() != form) {
            throw new CardConflictException("Default card reference belongs to another account owner");
        }
        return toSummary(existing);
    }

    private void ensureInitialPhysicalOrder(CardSummary card) {
        UUID cardId = UUID.fromString(card.cardId());
        if (cardOrderRepository.findByCardId(cardId).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        CardRecord cardRecord = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card was not found"));
        CardOrderRecord order = new CardOrderRecord(
                UUID.randomUUID(),
                cardRecord.userId(),
                cardRecord.profileId(),
                card.accountNumber(),
                null,
                null,
                null,
                null,
                null,
                "Initial physical card order",
                CardOrderStatus.PENDING,
                cardId,
                now,
                now
        );
        try {
            cardOrderRepository.save(order);
        } catch (DataIntegrityViolationException exception) {
            if (cardOrderRepository.findByCardId(cardId).isEmpty()) {
                throw new CardConflictException("Initial physical card order could not be created");
            }
        }
    }

    private CardRecord changeStatus(
            CardRecord card,
            CardStatus expectedStatus,
            CardStatus newStatus,
            String reason,
            CardActorType actorType,
            String actorId
    ) {
        if (card.status() != expectedStatus) {
            throw new CardBusinessRuleException(
                    "Card must be " + expectedStatus + " to change to " + newStatus
            );
        }
        Instant changedAt = clock.instant();
        if (!cardRepository.updateStatus(card.id(), expectedStatus, newStatus, changedAt)) {
            throw new CardConflictException("Card status changed concurrently");
        }
        saveHistory(card, expectedStatus, newStatus, reason, actorType, actorId, changedAt);
        return card.withStatus(newStatus, changedAt);
    }

    private void saveHistory(
            CardRecord card,
            CardStatus previousStatus,
            CardStatus newStatus,
            String reason,
            CardActorType actorType,
            String actorId,
            Instant createdAt
    ) {
        cardRepository.saveStatusHistory(new CardStatusHistoryRecord(
                UUID.randomUUID(),
                card.id(),
                card.userId(),
                card.profileId(),
                previousStatus,
                newStatus,
                reason,
                actorType,
                actorId,
                createdAt
        ));
    }

    private CardRecord ownedCard(UUID userId, String cardId) {
        return cardRepository.findByIdAndUserId(parseCardId(cardId), userId)
                .orElseThrow(() -> new CardNotFoundException("Card was not found"));
    }

    private CardRecord card(String cardId) {
        return cardRepository.findById(parseCardId(cardId))
                .orElseThrow(() -> new CardNotFoundException("Card was not found"));
    }

    private UUID resolveProfileId(VerifiedToken token, RequestContext context) {
        String requestedProfileId = context.account() == null ? null : context.account().profileId();
        if (requestedProfileId != null && !requestedProfileId.isBlank()) {
            UUID profileId = parseUuid(requestedProfileId, "context.account.profileId", "PROFILE_ID_UUID");
            if (token.profileId() != null && !token.profileId().equals(profileId)) {
                throw new CardUnauthorizedException("Unauthorized");
            }
            return profileId;
        }
        if (token.profileId() == null) {
            throw validation("context.account.profileId", "profileId is required", "PROFILE_ID_REQUIRED");
        }
        return token.profileId();
    }

    private void validateProfile(CardRecord card, UUID profileId) {
        if (!card.profileId().equals(profileId)) {
            throw new CardNotFoundException("Card was not found");
        }
    }

    private UUID parseCardId(String cardId) {
        return parseUuid(cardId, "data.cardId", "CARD_ID_UUID");
    }

    private UUID parseUuid(String value, String field, String code) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation(field, field.substring(field.lastIndexOf('.') + 1) + " must be a UUID", code);
        }
    }

    private CardValidationException validation(String field, String message, String code) {
        return new CardValidationException(List.of(new StatusDetail(field, message, code)));
    }

    private String defaultReason(String reason, String defaultValue) {
        return reason == null || reason.isBlank() ? defaultValue : reason.trim();
    }

    private CardStatusResponse statusResponse(CardRecord card) {
        return new CardStatusResponse(card.id().toString(), card.status(), card.updatedAt());
    }

    private CardSummary toSummary(CardRecord card) {
        return new CardSummary(
                card.id().toString(),
                card.accountNumber(),
                card.maskedPan(),
                card.form(),
                card.status(),
                card.expiresAt()
        );
    }
}
