package com.redbank.transfer.application;

public class TransferInsufficientFundsException extends RuntimeException {
    public TransferInsufficientFundsException(String message) {
        super(message);
    }
}
