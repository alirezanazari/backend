package com.redbank.transfer.application;

public class TransferUnauthorizedException extends RuntimeException {
    public TransferUnauthorizedException(String message) {
        super(message);
    }
}
