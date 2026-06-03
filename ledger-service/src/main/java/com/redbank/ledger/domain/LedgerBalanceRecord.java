package com.redbank.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerBalanceRecord(
        UUID ledgerAccountId,
        BigDecimal availableBalance,
        BigDecimal currentBalance,
        String currency,
        Instant updatedAt,
        long version
) {
}
