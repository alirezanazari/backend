package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(
        @NotNull ProfileType type,
        @NotBlank @Size(max = 120) String displayName,
        @Valid Image image
) {
}
