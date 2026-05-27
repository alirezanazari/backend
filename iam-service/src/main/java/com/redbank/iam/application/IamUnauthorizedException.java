package com.redbank.iam.application;

public class IamUnauthorizedException extends RuntimeException {
    public IamUnauthorizedException(String message) {
        super(message);
    }
}
