package com.redbank.kyc.application;

import java.util.List;
import java.util.UUID;

public record VerifiedToken(
        UUID userId,
        UUID deviceId,
        UUID profileId,
        List<String> roles,
        List<String> scopes
) {
    public VerifiedToken {
        roles = roles == null ? List.of() : List.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
