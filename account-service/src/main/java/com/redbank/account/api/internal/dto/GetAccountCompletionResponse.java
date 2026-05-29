package com.redbank.account.api.internal.dto;

import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.identity.AccountCompletionStatus;

public record GetAccountCompletionResponse(
        AccountCompletionStatus accountCompletionStatus,
        ProfileSummary activeProfile
) {
    public String activeProfileId() {
        return activeProfile == null ? null : activeProfile.profileId();
    }
}
