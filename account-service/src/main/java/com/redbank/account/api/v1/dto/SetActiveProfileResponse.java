package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.ProfileSummary;

public record SetActiveProfileResponse(
        ProfileSummary activeProfile
) {
}
