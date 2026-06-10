package com.redbank.kyc.domain;

import com.redbank.contracts.kyc.KycStatus;
import java.time.Instant;
import java.util.UUID;

public record KycCaseRecord(
        UUID id,
        UUID userId,
        KycStatus status,
        String rejectionReason,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public KycCaseRecord withStatus(KycStatus nextStatus, String reason, Instant completed, Instant updated) {
        return new KycCaseRecord(id, userId, nextStatus, reason, completed, createdAt, updated);
    }
}
