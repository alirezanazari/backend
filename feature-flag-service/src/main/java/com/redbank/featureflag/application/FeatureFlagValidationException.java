package com.redbank.featureflag.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class FeatureFlagValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public FeatureFlagValidationException(List<StatusDetail> details) {
        super("Invalid feature flag request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
