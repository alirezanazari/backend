package com.redbank.iam.api.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOtpChallengeRequest(
        @NotBlank String onboardingSessionId,
        @NotBlank String phoneNumber
) {
}
