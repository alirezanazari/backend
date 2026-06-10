package com.redbank.kyc.domain;

import com.redbank.contracts.kyc.KycStatus;
import java.time.Instant;
import java.util.UUID;

public record KycCaseHistoryRecord(
        UUID id,
        UUID kycCaseId,
        KycStatus previousStatus,
        KycStatus newStatus,
        String reason,
        UUID actorId,
        Instant createdAt
) {
}
