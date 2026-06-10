package com.redbank.featureflag.application;

public class FeatureFlagUnauthorizedException extends RuntimeException {
    public FeatureFlagUnauthorizedException(String message) {
        super(message);
    }
}
