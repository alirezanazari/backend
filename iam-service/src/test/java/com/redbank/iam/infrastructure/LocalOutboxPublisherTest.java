package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.OutboxEventRecord;
import com.redbank.iam.domain.OutboxEventStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalOutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-06-14T12:00:00Z");

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OutboxEventRecord event = event();
        when(repository.findPending(50, 5)).thenReturn(List.of(event));

        new LocalOutboxPublisher(
                repository,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry(),
                ObservationRegistry.create()
        ).publishPending();

        verify(eventPublisher).publishEvent(any(LocalDomainEvent.class));
        verify(repository).markPublished(event.id(), NOW);
    }

    @Test
    void failedPublicationIncrementsRetryState() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OutboxEventRecord event = event();
        when(repository.findPending(50, 5)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("listener failed"))
                .when(eventPublisher)
                .publishEvent(any(LocalDomainEvent.class));

        new LocalOutboxPublisher(
                repository,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry(),
                ObservationRegistry.create()
        ).publishPending();

        verify(repository).markAttemptFailed(event.id(), NOW, 5);
    }

    private OutboxEventRecord event() {
        return new OutboxEventRecord(
                UUID.randomUUID(),
                "USER",
                UUID.randomUUID().toString(),
                "USER_REGISTERED",
                "{}",
                OutboxEventStatus.PENDING,
                NOW,
                NOW,
                null,
                0
        );
    }
}
