package com.redbank.iam.application;

public class IamExternalServiceException extends RuntimeException {
    public IamExternalServiceException(String message) {
        super(message);
    }

    public IamExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
