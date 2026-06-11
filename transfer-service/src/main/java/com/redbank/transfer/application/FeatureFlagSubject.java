package com.redbank.transfer.application;

import java.util.UUID;

public record FeatureFlagSubject(
        UUID userId,
        UUID profileId,
        String accountNumber
) {
}
