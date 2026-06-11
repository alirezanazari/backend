package com.redbank.featureflag.api.v1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateFeatureFlagTargetingRequest(
        @NotBlank @Size(max = 160) String key,
        @Size(max = 1000) List<@NotNull @Valid FeatureFlagUserOverride> userOverrides,
        @Size(max = 100) List<@NotNull @Valid FeatureFlagTargetingRule> rules,
        @NotBlank @Size(max = 500) String reason
) {
}
