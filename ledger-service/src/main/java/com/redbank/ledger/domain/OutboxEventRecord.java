package com.redbank.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        OutboxEventStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        int retryCount
) {
}
