package com.redbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "redbank.internal")
public record InternalAccessProperties(
        String serviceTokenSecret,
        String completionGetTokenSecret
) {
    public static final String DEVELOP_SERVICE_TOKEN_SECRET = "local-develop-internal-service-token-change-me";

    @ConstructorBinding
    public InternalAccessProperties {
        serviceTokenSecret = defaultIfBlank(serviceTokenSecret);
        completionGetTokenSecret = defaultIfBlank(completionGetTokenSecret);
    }

    private static String defaultIfBlank(String value) {
        return value == null || value.isBlank() ? DEVELOP_SERVICE_TOKEN_SECRET : value;
    }
}
