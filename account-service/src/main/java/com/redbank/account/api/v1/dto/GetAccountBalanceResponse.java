package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.BalanceSummary;

public record GetAccountBalanceResponse(
        String accountNumber,
        String iban,
        BalanceSummary balance
) {
}
