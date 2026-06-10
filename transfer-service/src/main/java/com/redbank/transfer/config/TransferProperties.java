package com.redbank.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.transfer")
public record TransferProperties(
        long idempotencyTtlHours
) {
    public TransferProperties {
        if (idempotencyTtlHours <= 0) {
            idempotencyTtlHours = 24;
        }
    }
}
