package com.redbank.featureflag.api.v1.dto;

import com.redbank.featureflag.domain.TargetAttribute;
import com.redbank.featureflag.domain.TargetOperator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FeatureFlagCondition(
        @NotNull TargetAttribute attribute,
        @NotNull TargetOperator operator,
        @NotEmpty @Size(max = 100) List<@Size(max = 200) String> values
) {
}
