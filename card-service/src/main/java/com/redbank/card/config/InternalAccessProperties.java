package com.redbank.card.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret
) {
    public static final String DEVELOP_SECRET = "local-develop-internal-service-token-change-me";

    public InternalAccessProperties {
        if (serviceTokenSecret == null || serviceTokenSecret.isBlank()) {
            serviceTokenSecret = DEVELOP_SECRET;
        }
    }

    public boolean usesDevelopServiceTokenSecret() {
        return DEVELOP_SECRET.equals(serviceTokenSecret);
    }
}
