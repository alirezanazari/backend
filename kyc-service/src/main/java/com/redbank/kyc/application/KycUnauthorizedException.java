package com.redbank.kyc.application;

public class KycUnauthorizedException extends RuntimeException {
    public KycUnauthorizedException(String message) {
        super(message);
    }
}
