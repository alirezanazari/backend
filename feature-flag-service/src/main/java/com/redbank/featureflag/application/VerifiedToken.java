package com.redbank.featureflag.application;

import java.util.List;
import java.util.UUID;

public record VerifiedToken(
        UUID actorId,
        List<String> roles
) {
    public VerifiedToken {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
