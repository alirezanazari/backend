package com.redbank.gateway.debug;

import java.time.Instant;

public record DebugTokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {
}
