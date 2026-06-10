package com.redbank.kyc.api.v1.dto;

import com.redbank.contracts.kyc.KycStatus;

public record CompleteKycResponse(
        String kycCaseId,
        KycStatus status,
        Boolean referralRewardRequested
) {
}
