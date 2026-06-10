package com.redbank.featureflag.api.v1.dto;

import java.time.Instant;

public record UpdateFeatureFlagResponse(
        FeatureFlagSummary flag,
        Instant updatedAt
) {
}
