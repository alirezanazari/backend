package com.redbank.transfer.application;

public class TransferConflictException extends RuntimeException {
    public TransferConflictException(String message) {
        super(message);
    }
}
