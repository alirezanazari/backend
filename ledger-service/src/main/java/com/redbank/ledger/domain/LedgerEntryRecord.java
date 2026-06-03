package com.redbank.ledger.domain;

import com.redbank.contracts.ledger.LedgerDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryRecord(
        UUID id,
        UUID ledgerTransactionId,
        UUID ledgerAccountId,
        LedgerDirection direction,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}
