package com.redbank.pfm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.transfer-client")
public record TransferClientProperties(
        boolean enabled,
        String baseUrl
) {
}