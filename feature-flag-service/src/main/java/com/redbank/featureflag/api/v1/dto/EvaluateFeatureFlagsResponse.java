package com.redbank.featureflag.api.v1.dto;

import java.util.List;

public record EvaluateFeatureFlagsResponse(
        List<EvaluatedFeatureFlag> flags
) {
}
