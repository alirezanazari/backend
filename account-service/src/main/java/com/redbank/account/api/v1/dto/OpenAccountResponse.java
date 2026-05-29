package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.identity.AccountCompletionStatus;
import java.time.Instant;

public record OpenAccountResponse(
        ProfileSummary profile,
        AccountSummary account,
        Instant openedAt,
        AccountCompletionStatus accountCompletionStatus
) {
}
