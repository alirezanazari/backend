package com.redbank.contracts.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "redbankObservabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("management.endpoints.web.exposure.include", "health,info,metrics");
        properties.put("management.tracing.propagation.type", "w3c");
        properties.put("management.tracing.sampling.probability", "${REDBANK_TRACING_SAMPLING_PROBABILITY:1.0}");
        properties.put(
                "management.otlp.tracing.endpoint",
                "${REDBANK_OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:"
                        + "${REDBANK_OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces}"
        );
        properties.put(
                "management.otlp.metrics.export.url",
                "${REDBANK_OTEL_EXPORTER_OTLP_METRICS_ENDPOINT:"
                        + "${REDBANK_OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/metrics}"
        );
        properties.put("management.otlp.metrics.export.step", "${REDBANK_OTEL_METRICS_EXPORT_STEP:30s}");
        properties.put("management.metrics.tags.service", "${spring.application.name:unknown-service}");
        properties.put("management.metrics.tags.environment", "${REDBANK_OBSERVABILITY_ENVIRONMENT:local}");
        properties.put("management.metrics.tags.version", "${spring.application.version:unknown}");
        properties.put(
                "management.opentelemetry.resource-attributes.service.name",
                "${spring.application.name:unknown-service}"
        );
        properties.put(
                "management.opentelemetry.resource-attributes.service.version",
                "${spring.application.version:unknown}"
        );
        properties.put(
                "management.opentelemetry.resource-attributes.deployment.environment",
                "${REDBANK_OBSERVABILITY_ENVIRONMENT:local}"
        );
        properties.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");
        properties.put("management.metrics.distribution.percentiles-histogram.http.client.requests", "true");
        properties.put("spring.reactor.context-propagation", "auto");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }
}
