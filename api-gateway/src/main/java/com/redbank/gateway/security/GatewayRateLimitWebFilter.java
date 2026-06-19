package com.redbank.gateway.security;

import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayRateLimitWebFilter implements WebFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Status TOO_MANY_REQUESTS = new Status(
            StatusCode.LIMIT_EXCEEDED,
            "Too many requests",
            429,
            List.of()
    );

    private final GatewayRateLimitProperties properties;
    private final GatewayAccessPolicy accessPolicy;
    private final GatewayResponseWriter responseWriter;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public GatewayRateLimitWebFilter(
            GatewayRateLimitProperties properties,
            GatewayAccessPolicy accessPolicy,
            GatewayResponseWriter responseWriter
    ) {
        this(properties, accessPolicy, responseWriter, Clock.systemUTC());
    }

    GatewayRateLimitWebFilter(
            GatewayRateLimitProperties properties,
            GatewayAccessPolicy accessPolicy,
            GatewayResponseWriter responseWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.accessPolicy = accessPolicy;
        this.responseWriter = responseWriter;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        GatewayAccessPolicy.Access access = accessPolicy.access(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value()
        );
        if (access == GatewayAccessPolicy.Access.NOT_GATEWAY_API) {
            return chain.filter(exchange);
        }

        String key = rateLimitKey(exchange.getRequest(), access);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(properties.capacity(), clock.millis()));
        if (!bucket.tryConsume(properties, clock.millis())) {
            exchange.getResponse().getHeaders()
                    .set(HttpHeaders.RETRY_AFTER, Long.toString(properties.refillPeriodSeconds()));
            return responseWriter.write(exchange, TOO_MANY_REQUESTS);
        }
        trimBucketsIfNeeded();
        return chain.filter(exchange);
    }

    private String rateLimitKey(ServerHttpRequest request, GatewayAccessPolicy.Access access) {
        String bearerToken = bearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (bearerToken != null) {
            return "token:" + sha256(bearerToken);
        }
        return "client:" + access.name() + ":" + request.getPath().value() + ":" + clientAddress(request);
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    private String clientAddress(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for gateway rate limiting", exception);
        }
    }

    private void trimBucketsIfNeeded() {
        if (buckets.size() <= properties.maxBuckets()) {
            return;
        }
        long now = clock.millis();
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(properties, now));
    }

    private static final class Bucket {
        private double tokens;
        private long updatedAtMillis;

        private Bucket(int capacity, long nowMillis) {
            this.tokens = capacity;
            this.updatedAtMillis = nowMillis;
        }

        private synchronized boolean tryConsume(GatewayRateLimitProperties properties, long nowMillis) {
            refill(properties, nowMillis);
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        private synchronized boolean isFull(GatewayRateLimitProperties properties, long nowMillis) {
            refill(properties, nowMillis);
            return tokens >= properties.capacity();
        }

        private void refill(GatewayRateLimitProperties properties, long nowMillis) {
            long elapsedMillis = Math.max(0, nowMillis - updatedAtMillis);
            if (elapsedMillis == 0) {
                return;
            }
            double periods = elapsedMillis / (properties.refillPeriodSeconds() * 1000.0d);
            tokens = Math.min(properties.capacity(), tokens + periods * properties.refillTokens());
            updatedAtMillis = nowMillis;
        }
    }
}
