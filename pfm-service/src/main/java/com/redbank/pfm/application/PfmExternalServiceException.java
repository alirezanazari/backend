package com.redbank.pfm.application;

public class PfmExternalServiceException extends RuntimeException {
    public PfmExternalServiceException(String message) {
        super(message);
    }

    public PfmExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
