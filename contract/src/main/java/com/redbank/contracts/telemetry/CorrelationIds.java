package com.redbank.contracts.telemetry;

import java.util.UUID;

public final class CorrelationIds {
    private static final int MAX_LENGTH = 80;

    private CorrelationIds() {
    }

    public static String normalize(String candidate) {
        return isValid(candidate) ? candidate : UUID.randomUUID().toString();
    }

    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < candidate.length(); index++) {
            char value = candidate.charAt(index);
            if (!Character.isLetterOrDigit(value)
                    && value != '-'
                    && value != '_'
                    && value != '.'
                    && value != ':') {
                return false;
            }
        }
        return true;
    }
}
