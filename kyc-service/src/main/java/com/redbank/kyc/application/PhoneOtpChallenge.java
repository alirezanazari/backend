package com.redbank.kyc.application;

import java.time.Instant;

public record PhoneOtpChallenge(
        String challengeId,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
