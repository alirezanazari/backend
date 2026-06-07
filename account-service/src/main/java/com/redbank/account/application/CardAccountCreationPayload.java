package com.redbank.account.application;

public record CardAccountCreationPayload(
        String accountNumber,
        String userId,
        String profileId
) {
}
