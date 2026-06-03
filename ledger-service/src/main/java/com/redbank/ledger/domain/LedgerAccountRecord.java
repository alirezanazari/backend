package com.redbank.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerAccountRecord(
        UUID id,
        String externalAccountNumber,
        UUID ownerUserId,
        String currency,
        LedgerAccountStatus status,
        boolean allowNegativeBalance,
        Instant createdAt,
        Instant updatedAt
) {
}
