package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.account.BalanceSummary;

public record GetLedgerBalanceResponse(
        String externalAccountNumber,
        String ledgerAccountId,
        BalanceSummary balance
) {
}
