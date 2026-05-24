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

    public ProdSafetyGuard(Environment environment, DebugProperties debugProperties) {
        this.environment = environment;
        this.debugProperties = debugProperties;
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
    }
}
