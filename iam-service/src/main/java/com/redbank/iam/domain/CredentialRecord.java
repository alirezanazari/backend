package com.redbank.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record CredentialRecord(
        UUID id,
        UUID userId,
        String passwordHash,
        String passwordAlgorithm,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
