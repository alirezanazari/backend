package com.redbank.ledger.api.internal.dto;

import com.redbank.contracts.common.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReferralRewardRequest(
        @NotBlank @Size(max = 120) String referralRewardId,
        @NotBlank @Pattern(regexp = "\\d{18}") String referrerExternalAccountNumber,
        @NotNull @Valid Money amount
) {
}
