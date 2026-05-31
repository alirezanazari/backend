package com.redbank.account.api.v1.dto;

import com.redbank.contracts.common.Image;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditProfileRequest(
        @NotBlank String profileId,
        @NotBlank @Size(max = 120) String displayName,
        @Valid Image image
) {
}