package com.redbank.kyc.api.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record GetKycCompletionRequest(
        @NotBlank String userId
) {
}
