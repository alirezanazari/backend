package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.AccountStatus;
import java.time.Instant;

public record CloseAccountResponse(
        String accountNumber,
        AccountStatus status,
        Instant closedAt
) {
}
