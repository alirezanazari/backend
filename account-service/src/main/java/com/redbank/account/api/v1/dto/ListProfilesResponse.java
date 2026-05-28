package com.redbank.account.api.v1.dto;

import com.redbank.contracts.account.ProfileSummary;
import java.util.List;

public record ListProfilesResponse(
        List<ProfileSummary> profiles,
        String activeProfileId
) {
}
