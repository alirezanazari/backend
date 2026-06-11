package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagTargetingAuditRecord(
        UUID id,
        UUID featureFlagId,
        String key,
        String oldTargetingJson,
        String newTargetingJson,
        UUID changedBy,
        String reason,
        String requestId,
        Instant createdAt
) {
}
