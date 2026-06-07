package com.redbank.card.api.internal.dto;

import com.redbank.contracts.card.CardForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateDefaultCardRequest(
        @NotBlank @Pattern(regexp = "\\d{18}") String accountNumber,
        @NotBlank String userId,
        @NotBlank String profileId,
        @NotNull CardForm form
) {
}
