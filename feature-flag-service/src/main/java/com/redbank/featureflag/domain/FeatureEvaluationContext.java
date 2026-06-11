package com.redbank.featureflag.domain;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import java.math.BigDecimal;
import java.util.UUID;

public record FeatureEvaluationContext(
        UUID userId,
        UUID profileId,
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
