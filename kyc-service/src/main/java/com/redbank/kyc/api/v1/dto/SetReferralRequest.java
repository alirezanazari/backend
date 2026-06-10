package com.redbank.kyc.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetReferralRequest(
        @NotBlank
        @Size(min = 3, max = 80)
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        String referralCode
) {
}
