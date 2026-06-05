package com.redbank.ledger.api.internal.dto;

public record CloseLedgerAccountResponse(
        String ledgerAccountId,
        String status
) {
}
