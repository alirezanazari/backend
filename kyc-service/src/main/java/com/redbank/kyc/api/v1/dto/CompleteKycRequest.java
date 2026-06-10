package com.redbank.kyc.api.v1.dto;

import com.redbank.contracts.kyc.KycStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteKycRequest(
        @NotBlank @Size(max = 80) String userId,
        @NotNull KycStatus status,
        @Size(max = 500) String reason
) {
}
