package com.redbank.featureflag.application;

public class FeatureFlagNotFoundException extends RuntimeException {
    public FeatureFlagNotFoundException(String message) {
        super(message);
    }
}
