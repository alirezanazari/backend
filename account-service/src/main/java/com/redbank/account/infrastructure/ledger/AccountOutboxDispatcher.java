package com.redbank.account.infrastructure.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.account.application.AccountOutboxService;
import com.redbank.account.application.LedgerAccountClient;
import com.redbank.account.application.LedgerAccountCreationPayload;
import com.redbank.account.application.LedgerAccountLifecyclePayload;
import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.util.HashSet;
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
@ConditionalOnProperty(prefix = "redbank.ledger", name = "enabled", havingValue = "true")
public class AccountOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountOutboxDispatcher.class);
    private static final String DISPATCHER = "account-ledger";
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final Set<String> EVENT_TYPES = Set.of(
            AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED,
            AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CLOSE_REQUESTED,
            AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final LedgerAccountClient ledgerAccountClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public AccountOutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            LedgerAccountClient ledgerAccountClient,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.ledgerAccountClient = ledgerAccountClient;
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
        Set<String> attemptedAggregates = new HashSet<>();
        for (OutboxEventRecord event : outboxEventRepository.findPendingByEventTypes(
                EVENT_TYPES,
                BATCH_SIZE,
                MAX_RETRY_COUNT
        )) {
            String aggregateKey = event.aggregateType() + ":" + event.aggregateId();
            if (!attemptedAggregates.add(aggregateKey)) {
                continue;
            }
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
            boolean acknowledged = dispatchToLedger(event);
            if (!acknowledged) {
                throw new IllegalStateException("Ledger account lifecycle event was not acknowledged");
            }
            outboxEventRepository.markPublished(event.id(), clock.instant());
            recordOutboxEvent(event.eventType(), "published");
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("Account outbox event dispatch failed: {}", event.id(), exception);
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

    private boolean dispatchToLedger(OutboxEventRecord event) throws JsonProcessingException {
        return switch (event.eventType()) {
            case AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED -> {
                LedgerAccountCreationPayload payload = objectMapper.readValue(
                        event.payload(),
                        LedgerAccountCreationPayload.class
                );
                yield ledgerAccountClient.createAccount(
                        event.id(),
                        payload.accountNumber(),
                        UUID.fromString(payload.ownerUserId()),
                        payload.currency()
                );
            }
            case AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CLOSE_REQUESTED -> {
                LedgerAccountLifecyclePayload payload = objectMapper.readValue(
                        event.payload(),
                        LedgerAccountLifecyclePayload.class
                );
                yield ledgerAccountClient.close(event.id(), payload.accountNumber());
            }
            case AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED -> {
                LedgerAccountLifecyclePayload payload = objectMapper.readValue(
                        event.payload(),
                        LedgerAccountLifecyclePayload.class
                );
                yield ledgerAccountClient.reopen(event.id(), payload.accountNumber());
            }
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        };
    }
}
