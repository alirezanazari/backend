package com.redbank.ledger.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class LedgerValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public LedgerValidationException(List<StatusDetail> details) {
        super("Invalid ledger request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
