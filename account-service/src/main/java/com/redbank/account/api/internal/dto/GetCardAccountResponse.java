package com.redbank.account.api.internal.dto;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;

public record GetCardAccountResponse(
        String accountNumber,
        String userId,
        String profileId,
        AccountType type,
        AccountStatus status,
        String currency,
        Boolean eligible
) {
}
