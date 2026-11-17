package com.redbank.ledger.api.internal.dto;

import java.time.Instant;

public record LedgerTransactionResponse(
        String ledgerTransactionId,
        String status,
        Instant createdAt
) {
}
