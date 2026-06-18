package com.redbank.contracts.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityEnvironmentPostProcessorTest {
    private final ObservabilityEnvironmentPostProcessor processor = new ObservabilityEnvironmentPostProcessor();

    @Test
    void contributesSharedObservabilityDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "iam-service")
                .withProperty("spring.application.version", "0.1.0-test")
                .withProperty("REDBANK_OBSERVABILITY_ENVIRONMENT", "develop");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("management.tracing.propagation.type")).isEqualTo("w3c");
        assertThat(environment.resolvePlaceholders(environment.getProperty("management.otlp.tracing.endpoint")))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(environment.resolvePlaceholders(environment.getProperty("management.otlp.metrics.export.url")))
                .isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(environment.resolvePlaceholders(environment.getProperty("management.metrics.tags.service")))
                .isEqualTo("iam-service");
        assertThat(environment.resolvePlaceholders(environment.getProperty(
                "management.opentelemetry.resource-attributes.deployment.environment"
        ))).isEqualTo("develop");
    }

    @Test
    void keepsApplicationOrEnvironmentOverridesAheadOfDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.tracing.sampling.probability", "0.25")
                .withProperty("management.otlp.tracing.endpoint", "http://collector:4318/v1/traces");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.25");
        assertThat(environment.getProperty("management.otlp.tracing.endpoint"))
                .isEqualTo("http://collector:4318/v1/traces");
    }
}
