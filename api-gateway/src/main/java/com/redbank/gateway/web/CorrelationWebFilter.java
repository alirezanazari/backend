package com.redbank.gateway.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationWebFilter implements WebFilter {
    public static final String HEADER_NAME = "X-Correlation-Id";
    private static final int MAX_LENGTH = 80;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (!isValid(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(HEADER_NAME, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        MDC.put("correlationId", correlationId);
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signalType -> MDC.remove("correlationId"));
    }

    private boolean isValid(String correlationId) {
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < correlationId.length(); index++) {
            char value = correlationId.charAt(index);
            if (!Character.isLetterOrDigit(value)
                    && value != '-'
                    && value != '_'
                    && value != '.'
                    && value != ':') {
                return false;
            }
        }
        return true;
    }
}
