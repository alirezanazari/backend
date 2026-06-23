package com.redbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret,
        String vaultCreateTokenSecret
) {
    public static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    public InternalAccessProperties {
        if (serviceTokenSecret == null || serviceTokenSecret.isBlank()) {
            serviceTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
        if (vaultCreateTokenSecret == null || vaultCreateTokenSecret.isBlank()) {
            vaultCreateTokenSecret = DEVELOP_SERVICE_TOKEN_SECRET;
        }
    }

    public boolean usesDevelopServiceTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(serviceTokenSecret);
    }

    public boolean usesDevelopVaultCreateTokenSecret() {
        return DEVELOP_SERVICE_TOKEN_SECRET.equals(vaultCreateTokenSecret);
    }

    public boolean vaultCreateTokenReusesServiceTokenSecret() {
        return serviceTokenSecret.equals(vaultCreateTokenSecret);
    }
}
