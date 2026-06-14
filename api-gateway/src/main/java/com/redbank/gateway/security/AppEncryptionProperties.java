package com.redbank.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.gateway.app-encryption")
public record AppEncryptionProperties(
        boolean enabled,
        long keyExchangeTtlSeconds,
        int maxEncryptedBodyBytes
) {
    public AppEncryptionProperties {
        if (keyExchangeTtlSeconds <= 0) {
            keyExchangeTtlSeconds = 300;
        }
        if (maxEncryptedBodyBytes <= 0) {
            maxEncryptedBodyBytes = 1_048_576;
        }
    }
}
