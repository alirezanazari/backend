package com.redbank.pfm.web;

import com.redbank.contracts.telemetry.CorrelationIds;
import com.redbank.contracts.telemetry.TelemetryFields;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = TelemetryHeaders.CORRELATION_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private final String serviceName;

    public CorrelationIdFilter(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String correlationId = CorrelationIds.normalize(request.getHeader(HEADER_NAME));
        String requestId = requestId(request, correlationId);
        request.setAttribute(TelemetryMdc.CORRELATION_ID, correlationId);
        request.setAttribute(TelemetryMdc.REQUEST_ID, requestId);
        response.setHeader(HEADER_NAME, correlationId);
        response.setHeader(TelemetryHeaders.REQUEST_ID, requestId);
        putMdc(TelemetryMdc.CORRELATION_ID, correlationId);
        putMdc(TelemetryMdc.REQUEST_ID, requestId);
        putMdc(TelemetryMdc.USER_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_USER_ID));
        putMdc(TelemetryMdc.PROFILE_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_PROFILE_ID));
        putMdc(TelemetryMdc.DEVICE_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_DEVICE_ID));

        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            logCompletion(request, response, startedAt, failure);
            clearMdc();
        }
    }

    private String requestId(HttpServletRequest request, String correlationId) {
        String requestId = request.getHeader(TelemetryHeaders.REQUEST_ID);
        return CorrelationIds.isValid(requestId) ? requestId : correlationId;
    }

    private void logCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            Exception failure
    ) {
        long durationNanos = System.nanoTime() - startedAt;
        int status = failure == null || response.getStatus() >= 400 ? response.getStatus() : 500;
        String method = request.getMethod();
        String route = route(request);
        double durationMillis = durationNanos / 1_000_000.0;

        LOGGER.atInfo()
                .addKeyValue(TelemetryFields.EVENT_ACTION, TelemetryFields.REQUEST_COMPLETED_ACTION)
                .addKeyValue(TelemetryFields.EVENT_DATASET, this.serviceName)
                .addKeyValue(TelemetryFields.HTTP_REQUEST_METHOD, method)
                .addKeyValue(TelemetryFields.HTTP_ROUTE, route)
                .addKeyValue(TelemetryFields.HTTP_RESPONSE_STATUS_CODE, status)
                .addKeyValue(TelemetryFields.EVENT_DURATION, durationNanos)
                .addKeyValue(TelemetryFields.CLIENT_ADDRESS, request.getRemoteAddr())
                .addKeyValue(TelemetryFields.USER_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_USER_ID))
                .addKeyValue(TelemetryFields.PROFILE_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_PROFILE_ID))
                .addKeyValue(TelemetryFields.DEVICE_ID, request.getHeader(TelemetryHeaders.AUTHENTICATED_DEVICE_ID))
                .log("HTTP request completed method={} route={} status={} durationMs={}",
                        method, route, status, "%.3f".formatted(durationMillis));
    }

    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? request.getRequestURI() : pattern.toString();
    }

    private void putMdc(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private void clearMdc() {
        MDC.remove(TelemetryMdc.CORRELATION_ID);
        MDC.remove(TelemetryMdc.REQUEST_ID);
        MDC.remove(TelemetryMdc.USER_ID);
        MDC.remove(TelemetryMdc.PROFILE_ID);
        MDC.remove(TelemetryMdc.DEVICE_ID);
    }
}
