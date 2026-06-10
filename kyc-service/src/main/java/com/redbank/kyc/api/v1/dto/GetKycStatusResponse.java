package com.redbank.kyc.api.v1.dto;

import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.contracts.kyc.KycStatus;
import java.time.Instant;

public record GetKycStatusResponse(
        String kycCaseId,
        KycStatus status,
        AccountCompletionStatus accountCompletionStatus,
        Instant completedAt,
        String rejectionReason
) {
}
