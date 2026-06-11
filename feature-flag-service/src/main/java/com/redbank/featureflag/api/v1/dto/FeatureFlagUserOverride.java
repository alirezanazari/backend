package com.redbank.featureflag.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeatureFlagUserOverride(
        @NotBlank @Size(max = 80) String userId,
        @NotNull Boolean enabled
) {
}
