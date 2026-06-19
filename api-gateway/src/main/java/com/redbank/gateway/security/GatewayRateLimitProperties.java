package com.redbank.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.gateway.rate-limit")
public record GatewayRateLimitProperties(
        Boolean enabled,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        int maxBuckets
) {
    public GatewayRateLimitProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (capacity <= 0) {
            capacity = 120;
        }
        if (refillTokens <= 0) {
            refillTokens = capacity;
        }
        if (refillPeriodSeconds <= 0) {
            refillPeriodSeconds = 60;
        }
        if (maxBuckets <= 0) {
            maxBuckets = 50_000;
        }
    }
}
