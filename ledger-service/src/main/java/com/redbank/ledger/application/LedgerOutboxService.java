package com.redbank.ledger.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.ledger.domain.LedgerAccountRecord;
import com.redbank.ledger.domain.LedgerTransactionRecord;
import com.redbank.ledger.domain.OutboxEventRecord;
import com.redbank.ledger.domain.OutboxEventStatus;
import com.redbank.ledger.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerOutboxService {
    public static final String LEDGER_ACCOUNT_CREATED = "LEDGER_ACCOUNT_CREATED";
    public static final String LEDGER_ACCOUNT_CLOSED = "LEDGER_ACCOUNT_CLOSED";
    public static final String LEDGER_ACCOUNT_REOPENED = "LEDGER_ACCOUNT_REOPENED";
    public static final String LEDGER_TRANSACTION_POSTED = "LEDGER_TRANSACTION_POSTED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LedgerOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueAccountCreated(LedgerAccountRecord account) {
        enqueueAccount(account, LEDGER_ACCOUNT_CREATED);
    }

    public void enqueueAccountClosed(LedgerAccountRecord account) {
        enqueueAccount(account, LEDGER_ACCOUNT_CLOSED);
    }

    public void enqueueAccountReopened(LedgerAccountRecord account) {
        enqueueAccount(account, LEDGER_ACCOUNT_REOPENED);
    }

    public void enqueueTransactionPosted(LedgerTransactionRecord transaction) {
        enqueue(
                "LEDGER_TRANSACTION",
                transaction.id().toString(),
                LEDGER_TRANSACTION_POSTED,
                new LedgerTransactionPayload(
                        transaction.id(),
                        transaction.referenceType().name(),
                        transaction.referenceId(),
                        transaction.transactionType().name(),
                        transaction.status().name(),
                        transaction.createdAt()
                )
        );
    }

    private void enqueueAccount(LedgerAccountRecord account, String eventType) {
        enqueue(
                "LEDGER_ACCOUNT",
                account.id().toString(),
                eventType,
                new LedgerAccountPayload(
                        account.id(),
                        account.externalAccountNumber(),
                        account.ownerUserId(),
                        account.currency(),
                        account.status().name(),
                        account.updatedAt()
                )
        );
    }

    private void enqueue(String aggregateType, String aggregateId, String eventType, Object payload) {
        Instant now = clock.instant();
        outboxEventRepository.save(new OutboxEventRecord(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                serialize(payload),
                OutboxEventStatus.PENDING,
                now,
                now,
                null,
                0
        ));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Ledger outbox payload could not be created", exception);
        }
    }

    private record LedgerAccountPayload(
            UUID ledgerAccountId,
            String externalAccountNumber,
            UUID ownerUserId,
            String currency,
            String status,
            Instant occurredAt
    ) {
    }

    private record LedgerTransactionPayload(
            UUID ledgerTransactionId,
            String referenceType,
            String referenceId,
            String transactionType,
            String status,
            Instant occurredAt
    ) {
    }
}
