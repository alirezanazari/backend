package com.redbank.featureflag.api.v1.dto;

import java.time.Instant;
import java.util.List;

public record FeatureFlagTargetingResponse(
        String key,
        List<FeatureFlagUserOverride> userOverrides,
        List<FeatureFlagTargetingRule> rules,
        Instant updatedAt
) {
}
