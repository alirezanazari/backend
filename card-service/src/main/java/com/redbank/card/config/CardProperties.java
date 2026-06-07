package com.redbank.card.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.card")
public record CardProperties(
        boolean orderEnabled,
        int expiryYears,
        String bin
) {
    public CardProperties {
        if (expiryYears <= 0) {
            expiryYears = 5;
        }
        if (bin == null || !bin.matches("\\d{6}")) {
            bin = "621986";
        }
    }
}
