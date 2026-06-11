package com.redbank.pfm.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class PfmValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public PfmValidationException(List<StatusDetail> details) {
        super("Invalid PFM request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
