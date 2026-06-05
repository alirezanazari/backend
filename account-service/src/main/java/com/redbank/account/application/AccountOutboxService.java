package com.redbank.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.account.domain.BankAccountRecord;
import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.domain.OutboxEventStatus;
import com.redbank.account.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountOutboxService {
    public static final String AGGREGATE_TYPE_BANK_ACCOUNT = "BANK_ACCOUNT";
    public static final String EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED = "LEDGER_ACCOUNT_CREATE_REQUESTED";
    public static final String EVENT_TYPE_LEDGER_ACCOUNT_CLOSE_REQUESTED = "LEDGER_ACCOUNT_CLOSE_REQUESTED";
    public static final String EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED = "LEDGER_ACCOUNT_REOPEN_REQUESTED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueLedgerAccountCreation(BankAccountRecord account) {
        try {
            enqueue(
                    account.accountNumber(),
                    EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED,
                    objectMapper.writeValueAsString(new LedgerAccountCreationPayload(
                            account.accountNumber(),
                            account.userId().toString(),
                            account.currency()
                    ))
            );
        } catch (JsonProcessingException exception) {
            throw new AccountBusinessRuleException("Ledger account outbox payload could not be created");
        }
    }

    public void enqueueLedgerAccountClosure(BankAccountRecord account) {
        enqueueLifecycleEvent(account.accountNumber(), EVENT_TYPE_LEDGER_ACCOUNT_CLOSE_REQUESTED);
    }

    public void enqueueLedgerAccountReopen(BankAccountRecord account) {
        enqueueLifecycleEvent(account.accountNumber(), EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED);
    }

    private void enqueueLifecycleEvent(String accountNumber, String eventType) {
        try {
            enqueue(
                    accountNumber,
                    eventType,
                    objectMapper.writeValueAsString(new LedgerAccountLifecyclePayload(accountNumber))
            );
        } catch (JsonProcessingException exception) {
            throw new AccountBusinessRuleException("Ledger account outbox payload could not be created");
        }
    }

    private void enqueue(String accountNumber, String eventType, String payload) {
        Instant now = clock.instant();
        outboxEventRepository.save(new OutboxEventRecord(
                UUID.randomUUID(),
                AGGREGATE_TYPE_BANK_ACCOUNT,
                accountNumber,
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                now,
                now,
                null,
                0
        ));
    }
}
