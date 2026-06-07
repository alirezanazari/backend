package com.redbank.card.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnblockCardRequest(
        @NotBlank String cardId,
        @NotBlank @Size(max = 500) String reason
) {
}
