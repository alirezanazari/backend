package com.redbank.ledger.api.internal.dto;

public record LedgerTransactionResponse(
        String ledgerTransactionId,
        String status
) {
}
