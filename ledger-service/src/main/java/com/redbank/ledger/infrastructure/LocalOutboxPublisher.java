package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.OutboxEventRecord;
import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "redbank.events.publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LocalOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalOutboxPublisher.class);
    private static final String DISPATCHER = "ledger-local";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public LocalOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(fixedDelayString = "${redbank.events.publisher.delay-ms:5000}")
    public void publishPending() {
        Observation.createNotStarted("redbank.outbox.dispatch", observationRegistry)
                .lowCardinalityKeyValue("dispatcher", DISPATCHER)
                .observe(this::publishPendingObserved);
    }

    private void publishPendingObserved() {
        for (OutboxEventRecord event : outboxEventRepository.findPending(BATCH_SIZE, MAX_RETRY_COUNT)) {
            publishObserved(event);
        }
    }

    private void publishObserved(OutboxEventRecord event) {
        Observation.createNotStarted("redbank.outbox.event", observationRegistry)
                .lowCardinalityKeyValue("dispatcher", DISPATCHER)
                .lowCardinalityKeyValue("event.type", event.eventType())
                .observe(() -> publish(event));
    }

    private void publish(OutboxEventRecord event) {
        try {
            eventPublisher.publishEvent(new LocalDomainEvent(
                    event.id(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.eventType(),
                    event.payload(),
                    event.createdAt()
            ));
            outboxEventRepository.markPublished(event.id(), clock.instant());
            recordOutboxEvent(event.eventType(), "published");
        } catch (RuntimeException exception) {
            LOGGER.warn("Ledger outbox event publish failed: {}", event.id(), exception);
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
}
