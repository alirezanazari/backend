package com.redbank.card.application;

import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;

public class CardValidationException extends RuntimeException {
    private final List<StatusDetail> details;

    public CardValidationException(List<StatusDetail> details) {
        super("Invalid request");
        this.details = List.copyOf(details);
    }

    public List<StatusDetail> details() {
        return details;
    }
}
