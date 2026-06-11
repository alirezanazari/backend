package com.redbank.kyc.application;

public interface FeatureFlagClient {
    boolean isEnabled(String key, FeatureFlagSubject subject);
}
