package com.redbank.kyc.application;

public class KycBusinessRuleException extends RuntimeException {
    public KycBusinessRuleException(String message) {
        super(message);
    }
}
