package com.redbank.featureflag.application;

import com.redbank.featureflag.domain.FeatureEvaluationContext;
import java.util.UUID;

public interface FeatureEvaluationContextClient {
    FeatureEvaluationContext resolve(UUID userId, UUID profileId, String accountNumber);
}
