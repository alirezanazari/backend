package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagAuditRecord(
        UUID id,
        UUID featureFlagId,
        String key,
        boolean oldEnabled,
        boolean newEnabled,
        String oldDescription,
        String newDescription,
        boolean oldClientVisible,
        boolean newClientVisible,
        UUID changedBy,
        String reason,
        String requestId,
        Instant createdAt
) {
}
