package com.redbank.transfer.application;

public class TransferDuplicateRequestException extends RuntimeException {
    public TransferDuplicateRequestException(String message) {
        super(message);
    }
}
