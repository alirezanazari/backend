package com.redbank.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record OtpChallengeRecord(
        UUID id,
        UUID userId,
        UUID deviceId,
        String otpHash,
        OtpPurpose purpose,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        OtpChallengeStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
