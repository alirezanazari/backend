package com.redbank.featureflag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureFlagConditionRecord(
        UUID id,
        UUID targetingRuleId,
        TargetAttribute attribute,
        TargetOperator operator,
        List<String> values,
        Instant createdAt
) {
    public FeatureFlagConditionRecord {
        values = List.copyOf(values);
    }
}
