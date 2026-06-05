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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redbank.ledger", name = "enabled", havingValue = "true")
public class AccountOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountOutboxDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final LedgerAccountClient ledgerAccountClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountOutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            LedgerAccountClient ledgerAccountClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.ledgerAccountClient = ledgerAccountClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${redbank.account.outbox.dispatch-delay-ms:5000}")
    public void dispatchPending() {
        Set<String> attemptedAggregates = new HashSet<>();
        for (OutboxEventRecord event : outboxEventRepository.findPending(BATCH_SIZE, MAX_RETRY_COUNT)) {
            String aggregateKey = event.aggregateType() + ":" + event.aggregateId();
            if (!attemptedAggregates.add(aggregateKey)) {
                continue;
            }
            dispatch(event);
        }
    }

    private void dispatch(OutboxEventRecord event) {
        try {
            boolean acknowledged = dispatchToLedger(event);
            if (!acknowledged) {
                throw new IllegalStateException("Ledger account lifecycle event was not acknowledged");
            }
            outboxEventRepository.markPublished(event.id(), clock.instant());
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("Account outbox event dispatch failed: {}", event.id(), exception);
            outboxEventRepository.markAttemptFailed(event.id(), clock.instant(), MAX_RETRY_COUNT);
        }
    }

    private boolean dispatchToLedger(OutboxEventRecord event) throws JsonProcessingException {
        return switch (event.eventType()) {
            case AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED -> {
                LedgerAccountCreationPayload payload = objectMapper.readValue(
                        event.payload(),
                        LedgerAccountCreationPayload.class
                );
                yield ledgerAccountClient.createAccount(
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
                yield ledgerAccountClient.close(payload.accountNumber());
            }
            case AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED -> {
                LedgerAccountLifecyclePayload payload = objectMapper.readValue(
                        event.payload(),
                        LedgerAccountLifecyclePayload.class
                );
                yield ledgerAccountClient.reopen(payload.accountNumber());
            }
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        };
    }
}
