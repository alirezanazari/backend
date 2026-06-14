package com.redbank.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationWebFilterTest {
    private final CorrelationWebFilter filter = new CorrelationWebFilter();

    @Test
    void preservesValidCorrelationId() {
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(CorrelationWebFilter.HEADER_NAME, "request-123")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getFirst(CorrelationWebFilter.HEADER_NAME)).isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME))
                .isEqualTo("request-123");
    }

    @Test
    void replacesUnsafeCorrelationId() {
        AtomicReference<String> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(CorrelationWebFilter.HEADER_NAME, "unsafe value\n")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME));
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get()).isNotEqualTo("unsafe value\n").hasSize(36);
    }
}
