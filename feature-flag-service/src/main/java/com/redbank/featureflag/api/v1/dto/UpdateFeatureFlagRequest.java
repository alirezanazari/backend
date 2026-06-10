package com.redbank.featureflag.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFeatureFlagRequest(
        @NotBlank @Size(max = 160) String key,
        @NotNull Boolean enabled,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 500) String reason
) {
}
