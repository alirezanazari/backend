package com.redbank.kyc.application;

import java.util.UUID;

public record FeatureFlagSubject(
        UUID userId,
        UUID profileId,
        String accountNumber
) {
    public static FeatureFlagSubject forUser(UUID userId) {
        return new FeatureFlagSubject(userId, null, null);
    }
}
