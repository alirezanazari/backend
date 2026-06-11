package com.redbank.account.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GetFeatureEvaluationContextRequest(
        @NotBlank String userId,
        @Size(max = 80) String profileId,
        @Pattern(regexp = "\\d{18}") String accountNumber
) {
}
