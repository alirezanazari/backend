package com.redbank.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.gateway.rate-limit")
public record GatewayRateLimitProperties(
        Boolean enabled,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        int maxBuckets,
        Limit login
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
        login = login == null
                ? new Limit(10, 10, 60)
                : login.withDefaults(10, 60);
    }

    public Limit defaultLimit() {
        return new Limit(capacity, refillTokens, refillPeriodSeconds);
    }

    public record Limit(int capacity, int refillTokens, long refillPeriodSeconds) {
        Limit withDefaults(int defaultCapacity, long defaultRefillPeriodSeconds) {
            int effectiveCapacity = capacity > 0 ? capacity : defaultCapacity;
            int effectiveRefillTokens = refillTokens > 0 ? refillTokens : effectiveCapacity;
            long effectivePeriod = refillPeriodSeconds > 0 ? refillPeriodSeconds : defaultRefillPeriodSeconds;
            return new Limit(effectiveCapacity, effectiveRefillTokens, effectivePeriod);
        }
    }
}
