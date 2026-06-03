package com.redbank.ledger.application;

public class LedgerUnauthorizedException extends RuntimeException {
    public LedgerUnauthorizedException(String message) {
        super(message);
    }
}
