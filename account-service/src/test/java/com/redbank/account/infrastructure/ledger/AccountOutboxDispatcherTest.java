package com.redbank.account.infrastructure.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.account.application.AccountOutboxService;
import com.redbank.account.application.LedgerAccountClient;
import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.domain.OutboxEventStatus;
import com.redbank.account.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");

    @Test
    void dispatchesAccountLifecycleEventsInOrderAcrossPollingCycles() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        LedgerAccountClient ledgerClient = mock(LedgerAccountClient.class);
        String accountNumber = "123456789012345678";
        OutboxEventRecord close = lifecycleEvent(
                accountNumber,
                AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_CLOSE_REQUESTED
        );
        OutboxEventRecord reopen = lifecycleEvent(
                accountNumber,
                AccountOutboxService.EVENT_TYPE_LEDGER_ACCOUNT_REOPEN_REQUESTED
        );

        when(repository.findPendingByEventTypes(anySet(), anyInt(), anyInt()))
                .thenReturn(List.of(close, reopen))
                .thenReturn(List.of(reopen));
        when(ledgerClient.close(close.id(), accountNumber)).thenReturn(true);
        when(ledgerClient.reopen(reopen.id(), accountNumber)).thenReturn(true);

        AccountOutboxDispatcher dispatcher = new AccountOutboxDispatcher(
                repository,
                ledgerClient,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry(),
                ObservationRegistry.create()
        );

        dispatcher.dispatchPending();

        verify(ledgerClient).close(close.id(), accountNumber);
        verify(ledgerClient, never()).reopen(any(), any());
        verify(repository).markPublished(close.id(), NOW);

        dispatcher.dispatchPending();

        verify(ledgerClient).reopen(reopen.id(), accountNumber);
        verify(repository).markPublished(reopen.id(), NOW);
    }

    private OutboxEventRecord lifecycleEvent(String accountNumber, String eventType) {
        return new OutboxEventRecord(
                UUID.randomUUID(),
                AccountOutboxService.AGGREGATE_TYPE_BANK_ACCOUNT,
                accountNumber,
                eventType,
                "{\"accountNumber\":\"" + accountNumber + "\"}",
                OutboxEventStatus.PENDING,
                NOW,
                NOW,
                null,
                0
        );
    }
}
