package com.redbank.contracts.envelope;

import jakarta.validation.constraints.NotBlank;

public record ResponseContext(
        @NotBlank String requestId,
        String correlationId,
        String profileId,
        Status status
) {
    public ResponseContext(String requestId, String profileId, Status status) {
        this(requestId, null, profileId, status);
    }
}
