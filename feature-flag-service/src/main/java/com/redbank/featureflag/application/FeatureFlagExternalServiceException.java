package com.redbank.featureflag.application;

public class FeatureFlagExternalServiceException extends RuntimeException {
    public FeatureFlagExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
