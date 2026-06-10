package com.redbank.kyc.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReferralRewardRecord(
        UUID id,
        UUID referralId,
        String referrerAccountNumber,
        BigDecimal amount,
        String currency,
        ReferralRewardStatus status,
        String ledgerTransactionId,
        Instant requestedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
