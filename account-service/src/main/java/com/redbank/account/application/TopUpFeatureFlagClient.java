package com.redbank.account.application;

import java.util.UUID;

public interface TopUpFeatureFlagClient {
    TopUpPolicy getTopUpPolicy(UUID userId,
                          UUID profileId,
                          String accountNumber);
}
