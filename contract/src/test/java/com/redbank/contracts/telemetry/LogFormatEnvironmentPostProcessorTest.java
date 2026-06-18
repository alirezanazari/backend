package com.redbank.contracts.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class LogFormatEnvironmentPostProcessorTest {
    private final LogFormatEnvironmentPostProcessor processor = new LogFormatEnvironmentPostProcessor();

    @Test
    void translatesRedbankEcsFormatToSpringBootStructuredFormat() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REDBANK_LOG_FORMAT", "ecs");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    void translatesRedbankTextFormatToUnstructuredConsoleLogging() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("redbank.log-format", "text");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEmpty();
    }

    @Test
    void rejectsUnsupportedFormats() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("redbank.log-format", "json");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REDBANK_LOG_FORMAT must be either text or ecs");
    }
}
