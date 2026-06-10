package com.redbank.kyc.application;

public class KycConflictException extends RuntimeException {
    public KycConflictException(String message) {
        super(message);
    }
}
