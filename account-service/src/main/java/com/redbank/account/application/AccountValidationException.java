package com.redbank.account.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class AccountValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public AccountValidationException(List<StatusDetail> details) {
        super("Invalid request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
