package com.redbank.transfer.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class TransferValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public TransferValidationException(List<StatusDetail> details) {
        super("Invalid transfer request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
