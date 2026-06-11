package com.redbank.featureflag.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GetFeatureFlagTargetingRequest(
        @NotBlank @Size(max = 160) String key
) {
}
