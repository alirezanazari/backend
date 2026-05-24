package com.redbank.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.gateway.app-encryption")
public record AppEncryptionProperties(
        boolean enabled,
        long keyExchangeTtlSeconds
) {
    public AppEncryptionProperties {
        if (keyExchangeTtlSeconds <= 0) {
            keyExchangeTtlSeconds = 300;
        }
    }
}
