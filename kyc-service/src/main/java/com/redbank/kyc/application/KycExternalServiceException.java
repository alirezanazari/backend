package com.redbank.kyc.application;

public class KycExternalServiceException extends RuntimeException {
    public KycExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
