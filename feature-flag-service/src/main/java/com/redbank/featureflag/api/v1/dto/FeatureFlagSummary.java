package com.redbank.featureflag.api.v1.dto;

public record FeatureFlagSummary(
        String key,
        boolean enabled,
        String description,
        boolean clientVisible
) {
}
