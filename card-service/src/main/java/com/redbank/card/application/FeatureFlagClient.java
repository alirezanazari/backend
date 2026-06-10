package com.redbank.card.application;

public interface FeatureFlagClient {
    boolean isEnabled(String key);
}
