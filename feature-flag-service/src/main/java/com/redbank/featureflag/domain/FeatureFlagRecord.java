package com.redbank.featureflag.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FeatureFlagRecord(
        UUID id,
        String key,
        boolean enabled,
        String description,
        boolean clientVisible,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
    public FeatureFlagRecord withUpdate(
            boolean nextEnabled,
            String nextDescription,
            boolean nextClientVisible,
            UUID actorId,
            Instant nextUpdatedAt
    ) {
        return new FeatureFlagRecord(
                id,
                key,
                nextEnabled,
                nextDescription,
                nextClientVisible,
                actorId,
                createdAt,
                nextUpdatedAt,
                minAmount,
                maxAmount
        );
    }
}
