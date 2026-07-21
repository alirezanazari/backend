package com.redbank.kyc.application;

public class KycLimitExceededException extends RuntimeException {
    public KycLimitExceededException(String message) {
        super(message);
    }
}
