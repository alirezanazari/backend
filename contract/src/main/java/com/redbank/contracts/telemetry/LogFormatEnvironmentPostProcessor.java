package com.redbank.contracts.telemetry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class LogFormatEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "redbankTelemetryLogging";
    private static final String REDBANK_LOG_FORMAT = "redbank.log-format";
    private static final String BOOT_CONSOLE_FORMAT = "logging.structured.format.console";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configuredFormat = environment.getProperty(REDBANK_LOG_FORMAT);
        if (!StringUtils.hasText(configuredFormat)) {
            configuredFormat = environment.getProperty("REDBANK_LOG_FORMAT");
        }
        if (!StringUtils.hasText(configuredFormat)) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(BOOT_CONSOLE_FORMAT, bootFormat(configuredFormat));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private String bootFormat(String configuredFormat) {
        String normalized = configuredFormat.toLowerCase(Locale.ROOT).trim();
        if ("text".equals(normalized)) {
            return "";
        }
        if ("ecs".equals(normalized)) {
            return "ecs";
        }
        throw new IllegalArgumentException("REDBANK_LOG_FORMAT must be either text or ecs");
    }
}
