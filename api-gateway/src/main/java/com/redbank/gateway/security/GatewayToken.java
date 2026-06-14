package com.redbank.gateway.security;

import java.util.List;

public record GatewayToken(
        String subject,
        String profileId,
        String deviceId,
        String tokenId,
        List<String> roles,
        List<String> scopes
) {
    public GatewayToken {
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
