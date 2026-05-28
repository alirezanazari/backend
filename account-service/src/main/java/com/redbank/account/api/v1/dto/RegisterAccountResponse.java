package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.identity.AccountCompletionStatus;

public record RegisterAccountResponse(
        ProfileSummary profile,
        AccountSummary account,
        CardSummary card,
        Boolean ledgerAccountCreated,
        AccountCompletionStatus accountCompletionStatus
) {
}
