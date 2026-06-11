package com.redbank.featureflag.api.v1.dto;

import com.redbank.featureflag.domain.RuleMatchType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FeatureFlagTargetingRule(
        @NotBlank @Size(max = 160) String name,
        @PositiveOrZero int priority,
        @NotNull Boolean enabled,
        @NotNull RuleMatchType matchType,
        @NotNull Boolean active,
        @Size(max = 20) List<@NotNull @Valid FeatureFlagCondition> conditions
) {
}
