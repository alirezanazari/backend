package com.redbank.contracts.account;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfileSummary(
        @NotBlank String profileId,
        @NotNull ProfileType type,
        @NotBlank String displayName,
        @Valid Image image,
        @NotNull Boolean active
) {
}
