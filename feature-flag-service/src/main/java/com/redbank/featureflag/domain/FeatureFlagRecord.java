package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagRecord(
        UUID id,
        String key,
        boolean enabled,
        String description,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
    public FeatureFlagRecord withUpdate(
            boolean nextEnabled,
            String nextDescription,
            UUID actorId,
            Instant nextUpdatedAt
    ) {
        return new FeatureFlagRecord(
                id,
                key,
                nextEnabled,
                nextDescription,
                actorId,
                createdAt,
                nextUpdatedAt
        );
    }
}
