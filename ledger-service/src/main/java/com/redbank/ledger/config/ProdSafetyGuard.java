package com.redbank.ledger.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProdSafetyGuard implements ApplicationRunner {
    private final Environment environment;
    private final InternalAccessProperties internalAccessProperties;

    public ProdSafetyGuard(Environment environment, InternalAccessProperties internalAccessProperties) {
        this.environment = environment;
        this.internalAccessProperties = internalAccessProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }

        if (internalAccessProperties.serviceTokenSecret().contains("local-develop")
                || internalAccessProperties.serviceTokenSecret().contains("change-me")) {
            throw new IllegalStateException("ledger-service cannot start in prod with a develop internal service token");
        }
    }
}
