package com.redbank.iam.api.v1.dto;

import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.identity.AccountCompletionStatus;
import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AccountCompletionStatus accountCompletionStatus,
        ProfileSummary activeProfile,
        Boolean deviceVerificationRequired,
        String challengeId
) {
}
