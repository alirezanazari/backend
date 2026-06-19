package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayRateLimitWebFilterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-19T10:00:00Z"));

    @Test
    void rejectsGatewayRequestsAfterBucketIsExhausted() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 2, 2, 60, 100));
        MockServerWebExchange first = exchange("10.0.0.1");
        MockServerWebExchange second = exchange("10.0.0.1");
        MockServerWebExchange third = exchange("10.0.0.1");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(second, chain())).verifyComplete();
        StepVerifier.create(filter.filter(third, chain())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isNull();
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    void refillsBucketOverTime() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 1, 1, 60, 100));
        MockServerWebExchange first = exchange("10.0.0.2");
        MockServerWebExchange blocked = exchange("10.0.0.2");
        MockServerWebExchange afterRefill = exchange("10.0.0.2");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(blocked, chain())).verifyComplete();
        clock.advanceSeconds(60);
        StepVerifier.create(filter.filter(afterRefill, chain())).verifyComplete();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(afterRefill.getResponse().getStatusCode()).isNull();
    }

    @Test
    void bearerTokensHaveSeparateBucketsFromSameClientAddress() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 1, 1, 60, 100));
        MockServerWebExchange first = authenticatedExchange("10.0.0.3", "token-a");
        MockServerWebExchange second = authenticatedExchange("10.0.0.3", "token-b");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(second, chain())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isNull();
    }

    @Test
    void disabledLimiterPassesThrough() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(false, 1, 1, 60, 100));
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange("10.0.0.4"), current -> {
            calls.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();
        StepVerifier.create(filter.filter(exchange("10.0.0.4"), current -> {
            calls.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();

        assertThat(calls).hasValue(2);
    }

    private GatewayRateLimitWebFilter filter(GatewayRateLimitProperties properties) {
        return new GatewayRateLimitWebFilter(
                properties,
                new GatewayAccessPolicy(),
                new GatewayResponseWriter(new ObjectMapper()),
                clock
        );
    }

    private MockServerWebExchange exchange(String clientAddress) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/iam/login")
                .header("X-Forwarded-For", clientAddress)
                .build());
    }

    private MockServerWebExchange authenticatedExchange(String clientAddress, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/account/list")
                .header("X-Forwarded-For", clientAddress)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

    private org.springframework.web.server.WebFilterChain chain() {
        return exchange -> Mono.empty();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
