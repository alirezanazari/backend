package com.redbank.contracts.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record UserDisplay(
        String userId,
        @NotBlank String fullName,
        @Valid Image image
) {
}
