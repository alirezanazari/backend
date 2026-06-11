package com.redbank.contracts.pfm;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.ledger.LedgerDirection;
import java.time.Instant;

public record PfmTransactionSummary(
        String transactionId,
        String accountNumber,
        String title,
        Money amount,
        LedgerDirection direction,
        Instant occurredAt
) {
}
