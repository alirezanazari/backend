package com.redbank.iam.application;

import java.time.Instant;

public record TokenResult(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {
    public static TokenResult bearer(String accessToken, Instant expiresAt) {
        return new TokenResult(accessToken, "Bearer", expiresAt);
    }
}
