package com.redbank.kyc.api.v1.dto;

import java.time.Instant;

public record SetPhoneResponse(
        String challengeId,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
