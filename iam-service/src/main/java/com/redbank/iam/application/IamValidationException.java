package com.redbank.iam.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class IamValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public IamValidationException(List<StatusDetail> details) {
        super("Invalid request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
