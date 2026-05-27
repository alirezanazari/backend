package com.redbank.iam.api.v1.dto;

import com.redbank.contracts.identity.AccountCompletionStatus;
import java.time.Instant;

public record RegisterResponse(
        String userId,
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AccountCompletionStatus accountCompletionStatus
) {
}
