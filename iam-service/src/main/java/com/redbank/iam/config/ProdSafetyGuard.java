package com.redbank.iam.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProdSafetyGuard implements ApplicationRunner {
    private final Environment environment;
    private final DebugProperties debugProperties;
    private final JwtProperties jwtProperties;

    public ProdSafetyGuard(Environment environment, DebugProperties debugProperties, JwtProperties jwtProperties) {
        this.environment = environment;
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }

        if (debugProperties.enabled()) {
            throw new IllegalStateException("iam-service cannot start in prod with redbank.debug.enabled=true");
        }
        if (debugProperties.mockOtp().enabled()) {
            throw new IllegalStateException("iam-service cannot start in prod with redbank.debug.mock-otp.enabled=true");
        }
        if (jwtProperties.issuer().contains("develop") || jwtProperties.issuer().contains("debug")) {
            throw new IllegalStateException("iam-service cannot start in prod with a develop/debug JWT issuer");
        }
        if (jwtProperties.signingSecret().contains("local-develop") || jwtProperties.signingSecret().contains("change-me")) {
            throw new IllegalStateException("iam-service cannot start in prod with a develop JWT signing secret");
        }
    }
}
