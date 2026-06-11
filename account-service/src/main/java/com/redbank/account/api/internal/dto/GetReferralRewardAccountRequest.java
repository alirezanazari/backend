package com.redbank.account.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GetReferralRewardAccountRequest(
        @NotBlank String userId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {
}
