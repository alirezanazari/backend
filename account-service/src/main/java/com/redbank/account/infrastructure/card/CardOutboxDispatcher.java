package com.redbank.account.infrastructure.card;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.account.application.AccountOutboxService;
import com.redbank.account.application.CardAccountClient;
import com.redbank.account.application.CardAccountCreationPayload;
import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redbank.card", name = "enabled", havingValue = "true")
public class CardOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardOutboxDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final Set<String> EVENT_TYPES = Set.of(
            AccountOutboxService.EVENT_TYPE_DEFAULT_CARD_CREATE_REQUESTED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final CardAccountClient cardAccountClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CardOutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            CardAccountClient cardAccountClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.cardAccountClient = cardAccountClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${redbank.account.outbox.dispatch-delay-ms:5000}")
    public void dispatchPending() {
        for (OutboxEventRecord event : outboxEventRepository.findPendingByEventTypes(
                EVENT_TYPES,
                BATCH_SIZE,
                MAX_RETRY_COUNT
        )) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEventRecord event) {
        try {
            CardAccountCreationPayload payload = objectMapper.readValue(
                    event.payload(),
                    CardAccountCreationPayload.class
            );
            cardAccountClient.createDefaultCard(
                    payload.accountNumber(),
                    UUID.fromString(payload.userId()),
                    UUID.fromString(payload.profileId())
            );
            outboxEventRepository.markPublished(event.id(), clock.instant());
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("Default card outbox event dispatch failed: {}", event.id(), exception);
            outboxEventRepository.markAttemptFailed(event.id(), clock.instant(), MAX_RETRY_COUNT);
        }
    }
}
