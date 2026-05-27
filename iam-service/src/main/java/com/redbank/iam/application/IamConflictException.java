package com.redbank.iam.application;

public class IamConflictException extends RuntimeException {
    public IamConflictException(String message) {
        super(message);
    }
}
