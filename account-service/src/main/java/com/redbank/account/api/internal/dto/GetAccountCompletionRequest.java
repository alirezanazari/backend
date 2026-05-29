package com.redbank.account.api.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record GetAccountCompletionRequest(
        @NotBlank String userId
) {
}
