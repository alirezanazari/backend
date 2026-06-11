package com.redbank.account.api.internal.dto;

import com.redbank.contracts.account.AccountSummary;

public record CreateVaultAccountResponse(
        AccountSummary account
) {
}
