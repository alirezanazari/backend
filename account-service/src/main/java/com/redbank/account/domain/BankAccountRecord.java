package com.redbank.account.domain;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import java.time.Instant;
import java.util.UUID;

public record BankAccountRecord(
        UUID id,
        UUID profileId,
        UUID userId,
        String selectedJobId,
        String accountNumber,
        String iban,
        AccountType type,
        AccountStatus status,
        String currency,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
