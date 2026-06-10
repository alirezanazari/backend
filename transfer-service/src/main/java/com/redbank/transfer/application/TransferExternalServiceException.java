package com.redbank.transfer.application;

public class TransferExternalServiceException extends RuntimeException {
    public TransferExternalServiceException(String message) {
        super(message);
    }

    public TransferExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
