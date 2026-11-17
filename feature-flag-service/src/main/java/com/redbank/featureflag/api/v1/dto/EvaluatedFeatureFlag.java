package com.redbank.featureflag.api.v1.dto;

import java.math.BigDecimal;

public record EvaluatedFeatureFlag(
        String key,
        boolean enabled,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}
