package com.redbank.featureflag.api.v1.dto;

import java.util.List;

public record ListFeatureFlagsRequest(
        List<String> keys,
        Boolean includeDisabled
) {
}
