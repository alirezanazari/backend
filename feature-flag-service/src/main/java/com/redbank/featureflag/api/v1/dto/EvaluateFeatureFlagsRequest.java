package com.redbank.featureflag.api.v1.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record EvaluateFeatureFlagsRequest(
        @Size(max = 100) List<String> keys,
        String subjectUserId,
        String profileId,
        String accountNumber
) {
}
