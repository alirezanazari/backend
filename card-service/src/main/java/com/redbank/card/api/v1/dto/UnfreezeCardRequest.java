package com.redbank.card.api.v1.dto;

import jakarta.validation.constraints.NotBlank;

public record UnfreezeCardRequest(
        @NotBlank String cardId
) {
}
