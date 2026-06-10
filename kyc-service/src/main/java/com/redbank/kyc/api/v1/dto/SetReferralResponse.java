package com.redbank.kyc.api.v1.dto;

public record SetReferralResponse(
        String referralId,
        String referrerName,
        String status
) {
}
