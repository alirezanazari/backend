package com.redbank.contracts.envelope;

import jakarta.validation.constraints.NotBlank;

public record ResponseContext(
        @NotBlank String requestId,
        String profileId,
        Status status
) {
}
