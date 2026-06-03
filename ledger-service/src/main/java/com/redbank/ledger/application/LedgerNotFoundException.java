package com.redbank.ledger.application;

public class LedgerNotFoundException extends RuntimeException {
    public LedgerNotFoundException(String message) {
        super(message);
    }
}
