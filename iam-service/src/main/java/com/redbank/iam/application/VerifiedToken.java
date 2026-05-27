package com.redbank.iam.application;

import java.util.List;
import java.util.UUID;

public record VerifiedToken(
        UUID userId,
        UUID deviceId,
        List<String> roles,
        List<String> scopes
) {
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
