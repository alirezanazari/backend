package com.redbank.ledger.application;

public class LedgerInsufficientFundsException extends RuntimeException {
    public LedgerInsufficientFundsException(String message) {
        super(message);
    }
}
