package com.redbank.iam.application;

public class IamLimitExceededException extends RuntimeException {
    public IamLimitExceededException(String message) {
        super(message);
    }
}
