package com.redbank.featureflag.api.v1.dto;

import java.util.List;

public record ListFeatureFlagsResponse(
        List<FeatureFlagSummary> flags
) {
}
