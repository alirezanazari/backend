package com.redbank.account.api.v1.dto;

import jakarta.validation.constraints.NotBlank;

public record SetActiveProfileRequest(
        @NotBlank String profileId
) {
}
