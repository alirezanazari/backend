package com.redbank.ledger.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record LocalDomainEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant occurredAt
) {
}
