package com.redbank.ledger.api.internal.dto;

public record CreateLedgerAccountResponse(
        String ledgerAccountId,
        String status
) {
}
