package com.redbank.kyc.domain;

import java.time.Instant;
import java.util.UUID;

public record ReferralRecord(
        UUID id,
        UUID referredUserId,
        UUID referrerUserId,
        String referralCode,
        String referrerName,
        ReferralStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
