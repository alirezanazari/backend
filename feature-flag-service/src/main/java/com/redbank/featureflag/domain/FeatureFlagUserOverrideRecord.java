package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagUserOverrideRecord(
        UUID id,
        UUID featureFlagId,
        UUID userId,
        boolean enabled,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
