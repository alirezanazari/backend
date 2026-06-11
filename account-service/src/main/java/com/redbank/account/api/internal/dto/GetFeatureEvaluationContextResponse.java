package com.redbank.account.api.internal.dto;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import java.math.BigDecimal;

public record GetFeatureEvaluationContextResponse(
        String userId,
        String profileId,
        ProfileType profileType,
        String jobId,
        String accountNumber,
        AccountType accountType,
        AccountStatus accountStatus,
        String currency,
        BigDecimal availableBalance,
        BigDecimal currentBalance
) {
}
