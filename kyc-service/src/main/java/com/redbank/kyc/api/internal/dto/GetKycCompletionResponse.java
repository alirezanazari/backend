package com.redbank.kyc.api.internal.dto;

import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.contracts.kyc.KycStatus;

public record GetKycCompletionResponse(
        KycStatus kycStatus,
        AccountCompletionStatus accountCompletionStatus
) {
}
