package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.OutboxEventRecord;
import java.time.Clock;
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
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LocalOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${redbank.events.publisher.delay-ms:5000}")
    public void publishPending() {
        for (OutboxEventRecord event : outboxEventRepository.findPending(BATCH_SIZE, MAX_RETRY_COUNT)) {
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
            } catch (RuntimeException exception) {
                LOGGER.warn("IAM outbox event publish failed: {}", event.id(), exception);
                outboxEventRepository.markAttemptFailed(event.id(), clock.instant(), MAX_RETRY_COUNT);
            }
        }
    }
}
