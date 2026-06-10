package com.redbank.transfer.application;

import java.util.List;
import java.util.UUID;

public record VerifiedToken(
        UUID userId,
        UUID deviceId,
        UUID profileId,
        List<String> roles,
        List<String> scopes
) {
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
