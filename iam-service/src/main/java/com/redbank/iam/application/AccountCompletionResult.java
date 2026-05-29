package com.redbank.iam.application;

import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.identity.AccountCompletionStatus;
import java.util.UUID;

public record AccountCompletionResult(
        AccountCompletionStatus accountCompletionStatus,
        ProfileSummary activeProfile
) {
    public UUID activeProfileId() {
        if (activeProfile == null || activeProfile.profileId() == null || activeProfile.profileId().isBlank()) {
            return null;
        }
        return UUID.fromString(activeProfile.profileId());
    }
}
