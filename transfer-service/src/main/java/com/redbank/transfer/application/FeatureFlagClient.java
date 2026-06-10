package com.redbank.transfer.application;

public interface FeatureFlagClient {
    boolean isEnabled(String key);
}
