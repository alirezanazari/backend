package com.redbank.iam.api.internal.dto;

import java.time.Instant;

public record CreateOtpChallengeResponse(
        String challengeId,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
