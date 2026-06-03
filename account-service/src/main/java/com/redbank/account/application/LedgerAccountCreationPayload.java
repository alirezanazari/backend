package com.redbank.account.application;

public record LedgerAccountCreationPayload(
        String accountNumber,
        String ownerUserId,
        String currency
) {
}
