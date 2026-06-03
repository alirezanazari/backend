package com.redbank.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerTransactionRecord(
        UUID id,
        LedgerReferenceType referenceType,
        String referenceId,
        String idempotencyKey,
        LedgerTransactionType transactionType,
        LedgerTransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
