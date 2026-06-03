package com.redbank.ledger.application;

public class LedgerConflictException extends RuntimeException {
    public LedgerConflictException(String message) {
        super(message);
    }
}
