package com.redbank.featureflag.api.v1.dto;

public record EvaluatedFeatureFlag(
        String key,
        boolean enabled
) {
}
