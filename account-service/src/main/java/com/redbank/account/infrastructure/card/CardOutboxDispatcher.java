package com.redbank.account.infrastructure.card;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.account.application.AccountOutboxService;
import com.redbank.account.application.CardAccountClient;
import com.redbank.account.application.CardAccountCreationPayload;
import com.redbank.account.application.CardAccountLifecyclePayload;
import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redbank.card", name = "enabled", havingValue = "true")
public class CardOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardOutboxDispatcher.class);
    private static final String DISPATCHER = "account-card";
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final Set<String> EVENT_TYPES = Set.of(
            AccountOutboxService.EVENT_TYPE_DEFAULT_CARD_CREATE_REQUESTED,
            AccountOutboxService.EVENT_TYPE_CARD_ACCOUNT_CLOSE_REQUESTED,
            AccountOutboxService.EVENT_TYPE_CARD_ACCOUNT_REOPEN_REQUESTED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final CardAccountClient cardAccountClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public CardOutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            CardAccountClient cardAccountClient,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.cardAccountClient = cardAccountClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(fixedDelayString = "${redbank.account.outbox.dispatch-delay-ms:5000}")
    public void dispatchPending() {
        Observation.createNotStarted("redbank.outbox.dispatch", observationRegistry)
                .lowCardinalityKeyValue("dispatcher", DISPATCHER)
                .observe(this::dispatchPendingObserved);
    }

    private void dispatchPendingObserved() {
        for (OutboxEventRecord event : outboxEventRepository.findPendingByEventTypes(
                EVENT_TYPES,
                BATCH_SIZE,
                MAX_RETRY_COUNT
        )) {
            dispatchObserved(event);
        }
    }

    private void dispatchObserved(OutboxEventRecord event) {
        Observation.createNotStarted("redbank.outbox.event", observationRegistry)
                .lowCardinalityKeyValue("dispatcher", DISPATCHER)
                .lowCardinalityKeyValue("event.type", event.eventType())
                .observe(() -> dispatch(event));
    }

    private void dispatch(OutboxEventRecord event) {
        try {
            boolean acknowledged = dispatchToCard(event);
            if (!acknowledged) {
                throw new IllegalStateException("Card account lifecycle event was not acknowledged");
            }
            outboxEventRepository.markPublished(event.id(), clock.instant());
            recordOutboxEvent(event.eventType(), "published");
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("Default card outbox event dispatch failed: {}", event.id(), exception);
            outboxEventRepository.markAttemptFailed(event.id(), clock.instant(), MAX_RETRY_COUNT);
            recordOutboxEvent(event.eventType(), "failed");
        }
    }

    private void recordOutboxEvent(String eventType, String outcome) {
        meterRegistry.counter(
                "redbank.outbox.events",
                "dispatcher",
                DISPATCHER,
                "event.type",
                eventType,
                "outcome",
                outcome
        ).increment();
    }

    private boolean dispatchToCard(OutboxEventRecord event) throws JsonProcessingException {
        return switch (event.eventType()) {
            case AccountOutboxService.EVENT_TYPE_DEFAULT_CARD_CREATE_REQUESTED -> {
                CardAccountCreationPayload payload = objectMapper.readValue(
                        event.payload(),
                        CardAccountCreationPayload.class
                );
                yield cardAccountClient.createDefaultCard(
                        event.id(),
                        payload.accountNumber(),
                        UUID.fromString(payload.userId()),
                        UUID.fromString(payload.profileId()),
                        payload.form()
                ) != null;
            }
            case AccountOutboxService.EVENT_TYPE_CARD_ACCOUNT_CLOSE_REQUESTED -> {
                CardAccountLifecyclePayload payload = objectMapper.readValue(
                        event.payload(),
                        CardAccountLifecyclePayload.class
                );
                yield cardAccountClient.close(event.id(), payload.accountNumber());
            }
            case AccountOutboxService.EVENT_TYPE_CARD_ACCOUNT_REOPEN_REQUESTED -> {
                CardAccountLifecyclePayload payload = objectMapper.readValue(
                        event.payload(),
                        CardAccountLifecyclePayload.class
                );
                yield cardAccountClient.reopen(event.id(), payload.accountNumber());
            }
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        };
    }
}
