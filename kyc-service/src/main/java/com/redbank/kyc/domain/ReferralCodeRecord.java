package com.redbank.kyc.domain;

import java.time.Instant;
import java.util.UUID;

public record ReferralCodeRecord(
        UUID id,
        UUID userId,
        String code,
        String displayName,
        Instant createdAt
) {
}
