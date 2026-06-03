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
        Instant now = clock.instant();
        try {
            outboxEventRepository.save(new OutboxEventRecord(
                    UUID.randomUUID(),
                    AGGREGATE_TYPE_BANK_ACCOUNT,
                    account.accountNumber(),
                    EVENT_TYPE_LEDGER_ACCOUNT_CREATE_REQUESTED,
                    objectMapper.writeValueAsString(new LedgerAccountCreationPayload(
                            account.accountNumber(),
                            account.userId().toString(),
                            account.currency()
                    )),
                    OutboxEventStatus.PENDING,
                    now,
                    now,
                    null,
                    0
            ));
        } catch (JsonProcessingException exception) {
            throw new AccountBusinessRuleException("Ledger account outbox payload could not be created");
        }
    }
}
