package com.redbank.card.api.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpireCardRequest(
        @NotBlank String cardId,
        @Size(max = 500) String reason
) {
}
