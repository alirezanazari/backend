package com.redbank.kyc.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class KycValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public KycValidationException(List<StatusDetail> details) {
        super("Invalid request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
