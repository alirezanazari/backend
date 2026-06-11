package com.redbank.account.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVaultAccountRequest(
        @NotBlank String userId,
        @NotBlank String profileId,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
