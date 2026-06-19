package com.redbank.gateway.web;

import com.redbank.contracts.telemetry.CorrelationIds;
import com.redbank.contracts.telemetry.TelemetryFields;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationWebFilter implements WebFilter {
    public static final String HEADER_NAME = TelemetryHeaders.CORRELATION_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationWebFilter.class);

    private final String serviceName;

    public CorrelationWebFilter(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        String correlationId = CorrelationIds.normalize(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        String requestId = requestId(exchange, correlationId);
        exchange.getAttributes().put(TelemetryMdc.CORRELATION_ID, correlationId);
        exchange.getAttributes().put(TelemetryMdc.REQUEST_ID, requestId);

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(HEADER_NAME, correlationId)
                .header(TelemetryHeaders.REQUEST_ID, requestId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        exchange.getResponse().getHeaders().set(TelemetryHeaders.REQUEST_ID, requestId);

        return chain.filter(exchange.mutate().request(request).build())
                .doOnError(failure::set)
                .doFinally(signalType -> logCompletion(exchange, request, startedAt, failure.get()));
    }

    private String requestId(ServerWebExchange exchange, String correlationId) {
        String requestId = exchange.getRequest().getHeaders().getFirst(TelemetryHeaders.REQUEST_ID);
        return CorrelationIds.isValid(requestId) ? requestId : correlationId;
    }

    private void logCompletion(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            long startedAt,
            Throwable failure
    ) {
        long durationNanos = System.nanoTime() - startedAt;
        int status = responseStatus(exchange, failure);
        String method = request.getMethod().name();
        String route = request.getPath().pathWithinApplication().value();
        double durationMillis = durationNanos / 1_000_000.0;
        String correlationId = exchange.getAttribute(TelemetryMdc.CORRELATION_ID);
        String requestId = exchange.getAttribute(TelemetryMdc.REQUEST_ID);

        putMdc(TelemetryMdc.CORRELATION_ID, correlationId);
        putMdc(TelemetryMdc.REQUEST_ID, requestId);
        try {
            LOGGER.atInfo()
                    .addKeyValue(TelemetryFields.EVENT_ACTION, TelemetryFields.REQUEST_COMPLETED_ACTION)
                    .addKeyValue(TelemetryFields.EVENT_DATASET, this.serviceName)
                    .addKeyValue(TelemetryFields.HTTP_REQUEST_METHOD, method)
                    .addKeyValue(TelemetryFields.HTTP_ROUTE, route)
                    .addKeyValue(TelemetryFields.HTTP_RESPONSE_STATUS_CODE, status)
                    .addKeyValue(TelemetryFields.EVENT_DURATION, durationNanos)
                    .addKeyValue(TelemetryFields.CLIENT_ADDRESS, clientAddress(request))
                    .addKeyValue(TelemetryFields.CORRELATION_ID, correlationId)
                    .addKeyValue(TelemetryFields.REQUEST_ID, requestId)
                    .log("HTTP request completed method={} route={} status={} durationMs={}",
                            method, route, status, "%.3f".formatted(durationMillis));
        } finally {
            MDC.remove(TelemetryMdc.CORRELATION_ID);
            MDC.remove(TelemetryMdc.REQUEST_ID);
        }
    }

    private int responseStatus(ServerWebExchange exchange, Throwable failure) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        if (statusCode == null) {
            return failure == null ? 200 : 500;
        }
        return failure == null || statusCode.value() >= 400 ? statusCode.value() : 500;
    }

    private String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null) {
            return null;
        }
        return remoteAddress.getAddress() == null
                ? remoteAddress.getHostString()
                : remoteAddress.getAddress().getHostAddress();
    }

    private void putMdc(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
