package com.redbank.transfer.application;

import java.util.List;
import java.util.Map;

public interface FeatureFlagClient {
    Map<String, Boolean> evaluate(List<String> keys, FeatureFlagSubject subject);

    default boolean isEnabled(String key, FeatureFlagSubject subject) {
        return Boolean.TRUE.equals(evaluate(List.of(key), subject).get(key));
    }
}
