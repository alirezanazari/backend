package com.redbank.account.domain;

import java.time.Instant;
import java.util.UUID;

public record ProfileStatusHistoryRecord(
        UUID id,
        UUID profileId,
        UUID userId,
        ProfileStatus previousStatus,
        ProfileStatus newStatus,
        String reasonText,
        Instant createdAt
) {
}
