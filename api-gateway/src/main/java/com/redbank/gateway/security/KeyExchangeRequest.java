package com.redbank.gateway.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeyExchangeRequest(
        @NotBlank @Size(max = 4096) String clientPublicKey
) {
}
