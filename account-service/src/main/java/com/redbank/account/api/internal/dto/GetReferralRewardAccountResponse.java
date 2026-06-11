package com.redbank.account.api.internal.dto;

public record GetReferralRewardAccountResponse(
        String userId,
        String accountNumber,
        String currency
) {
}
