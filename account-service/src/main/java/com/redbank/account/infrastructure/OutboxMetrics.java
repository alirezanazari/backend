package com.redbank.account.infrastructure;

import com.redbank.account.domain.OutboxEventStatus;
import java.time.Clock;
import java.util.function.DoubleSupplier;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics {
    private static final String PRODUCER = "account";

    private final OutboxEventRepository repository;
    private final Clock clock;

    OutboxMetrics(OutboxEventRepository repository, MeterRegistry meterRegistry, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        register(meterRegistry);
    }

    private void register(MeterRegistry meterRegistry) {
        Gauge.builder("redbank.outbox.backlog", this, OutboxMetrics::pendingBacklog)
                .baseUnit("events")
                .tag("producer", PRODUCER)
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("redbank.outbox.backlog", this, OutboxMetrics::deadLetterBacklog)
                .baseUnit("events")
                .tag("producer", PRODUCER)
                .tag("status", "dead_letter")
                .register(meterRegistry);
        Gauge.builder("redbank.outbox.retry.count", this, OutboxMetrics::pendingRetryCount)
                .baseUnit("attempts")
                .tag("producer", PRODUCER)
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("redbank.outbox.oldest.pending.age", this, OutboxMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .tag("producer", PRODUCER)
                .register(meterRegistry);
    }

    private double pendingBacklog() {
        return safe(() -> repository.countByStatus(OutboxEventStatus.PENDING));
    }

    private double deadLetterBacklog() {
        return safe(() -> repository.countByStatus(OutboxEventStatus.DEAD_LETTER));
    }

    private double pendingRetryCount() {
        return safe(() -> repository.totalRetryCountByStatus(OutboxEventStatus.PENDING));
    }

    private double oldestPendingAgeSeconds() {
        return safe(() -> repository.oldestPendingAgeSeconds(clock.instant()));
    }

    private double safe(DoubleSupplier supplier) {
        try {
            return supplier.getAsDouble();
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }
}
