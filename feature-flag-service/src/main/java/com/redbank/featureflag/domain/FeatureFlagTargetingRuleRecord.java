package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureFlagTargetingRuleRecord(
        UUID id,
        UUID featureFlagId,
        String name,
        int priority,
        boolean enabled,
        RuleMatchType matchType,
        boolean active,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        List<FeatureFlagConditionRecord> conditions
) {
    public FeatureFlagTargetingRuleRecord {
        conditions = List.copyOf(conditions);
    }
}
