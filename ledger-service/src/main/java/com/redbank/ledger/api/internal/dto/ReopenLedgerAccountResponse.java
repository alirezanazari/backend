package com.redbank.ledger.api.internal.dto;

public record ReopenLedgerAccountResponse(
        String ledgerAccountId,
        String status
) {
}
