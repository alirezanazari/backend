package com.redbank.kyc.application;

import com.redbank.contracts.common.Money;

public record ReferralRewardCommand(
        String referralRewardId,
        String referrerExternalAccountNumber,
        Money amount
) {
}
