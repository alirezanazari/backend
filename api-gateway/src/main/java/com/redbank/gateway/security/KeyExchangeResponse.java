package com.redbank.gateway.security;

import java.time.Instant;

public record KeyExchangeResponse(
        String keyExchangeId,
        String serverPublicKey,
        Instant expiresAt
) {
}
