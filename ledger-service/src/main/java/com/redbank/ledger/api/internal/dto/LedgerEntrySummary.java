package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.ledger.LedgerDirection;
import java.time.Instant;

public record LedgerEntrySummary(
        String entryId,
        String ledgerTransactionId,
        LedgerDirection direction,
        Money amount,
        Instant createdAt
) {
}
