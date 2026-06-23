package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.net.InetSocketAddress;
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
    void unverifiedBearerValuesUseTheSameClientBucket() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 1, 1, 60, 100));
        MockServerWebExchange first = bearerExchange("/api/v1/account/list", "10.0.0.3", "invalid-token-a");
        MockServerWebExchange second = bearerExchange("/api/v1/account/list", "10.0.0.3", "invalid-token-b");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(second, chain())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void spoofedForwardedForHeadersDoNotSplitClientBuckets() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 1, 1, 60, 100));
        MockServerWebExchange first = exchange("/api/v1/iam/login", "10.0.0.4", "198.51.100.1");
        MockServerWebExchange second = exchange("/api/v1/iam/login", "10.0.0.4", "198.51.100.2");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(second, chain())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void wildcardServicePathsUseBoundedRouteBuckets() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 1, 1, 60, 100));
        MockServerWebExchange first = bearerExchange("/api/v1/account/random-a", "10.0.0.5", "invalid-token-a");
        MockServerWebExchange second = bearerExchange("/api/v1/account/random-b", "10.0.0.5", "invalid-token-b");

        StepVerifier.create(filter.filter(first, chain())).verifyComplete();
        StepVerifier.create(filter.filter(second, chain())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void maxBucketsIsAHardLimitForNewClientBuckets() {
        GatewayRateLimitWebFilter filter = filter(new GatewayRateLimitProperties(true, 2, 2, 60, 1));
        MockServerWebExchange firstClient = exchange("10.0.0.6");
        MockServerWebExchange sameClient = exchange("10.0.0.6");
        MockServerWebExchange secondClient = exchange("10.0.0.7");

        StepVerifier.create(filter.filter(firstClient, chain())).verifyComplete();
        StepVerifier.create(filter.filter(sameClient, chain())).verifyComplete();
        StepVerifier.create(filter.filter(secondClient, chain())).verifyComplete();

        assertThat(firstClient.getResponse().getStatusCode()).isNull();
        assertThat(sameClient.getResponse().getStatusCode()).isNull();
        assertThat(secondClient.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
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
        return exchange("/api/v1/iam/login", clientAddress, null);
    }

    private MockServerWebExchange exchange(String path, String clientAddress, String forwardedFor) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(clientAddress, 12345));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private MockServerWebExchange bearerExchange(String path, String clientAddress, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(clientAddress, 12345))
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
