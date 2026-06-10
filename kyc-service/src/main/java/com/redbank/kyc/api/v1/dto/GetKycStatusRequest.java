package com.redbank.kyc.api.v1.dto;

import jakarta.validation.constraints.Size;

public record GetKycStatusRequest(
        @Size(max = 80) String userId
) {
}
