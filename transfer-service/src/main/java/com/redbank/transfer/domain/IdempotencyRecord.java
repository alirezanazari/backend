package com.redbank.transfer.domain;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
        UUID id,
        UUID userId,
        String key,
        String requestHash,
        UUID transferId,
        String responseSnapshot,
        IdempotencyStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
