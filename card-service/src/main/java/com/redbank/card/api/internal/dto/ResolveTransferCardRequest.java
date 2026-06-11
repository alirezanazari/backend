package com.redbank.card.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveTransferCardRequest(
        @NotBlank @Size(max = 80) String cardNumber,
        String userId,
        String profileId,
        Boolean requireOwnership
) {
}
