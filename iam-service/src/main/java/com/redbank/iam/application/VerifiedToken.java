package com.redbank.iam.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VerifiedToken(
        String jti,
        Instant expiresAt,
        UUID userId,
        UUID deviceId,
        List<String> roles,
        List<String> scopes
) {
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
