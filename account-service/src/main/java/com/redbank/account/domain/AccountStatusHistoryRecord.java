package com.redbank.account.domain;

import com.redbank.contracts.account.AccountStatus;
import java.time.Instant;
import java.util.UUID;

public record AccountStatusHistoryRecord(
        UUID id,
        UUID accountId,
        UUID userId,
        UUID profileId,
        AccountStatus previousStatus,
        AccountStatus newStatus,
        String reasonText,
        Instant createdAt
) {
}
