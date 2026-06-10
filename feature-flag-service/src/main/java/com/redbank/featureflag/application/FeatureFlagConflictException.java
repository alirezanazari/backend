package com.redbank.featureflag.application;

public class FeatureFlagConflictException extends RuntimeException {
    public FeatureFlagConflictException(String message) {
        super(message);
    }
}
