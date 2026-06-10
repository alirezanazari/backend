package com.redbank.transfer.application;

public class TransferFeatureDisabledException extends RuntimeException {
    public TransferFeatureDisabledException(String message) {
        super(message);
    }
}
