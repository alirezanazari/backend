package com.redbank.iam.api.v1.dto;

import com.redbank.contracts.identity.AccountCompletionStatus;
import java.time.Instant;

public record DeviceVerifyResponse(
        String deviceId,
        String deviceStatus,
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AccountCompletionStatus accountCompletionStatus
) {
}
