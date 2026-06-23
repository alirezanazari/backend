package com.redbank.gateway.security;

import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.net.InetSocketAddress;
import java.time.Clock;
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
    private final Object bucketAllocationLock = new Object();

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
        long nowMillis = clock.millis();
        Bucket bucket = bucketFor(key, nowMillis);
        if (bucket == null || !bucket.tryConsume(properties, nowMillis)) {
            return tooManyRequests(exchange);
        }
        return chain.filter(exchange);
    }

    private String rateLimitKey(ServerHttpRequest request, GatewayAccessPolicy.Access access) {
        String routeKey = accessPolicy.rateLimitRouteKey(request.getMethod(), request.getPath().value(), access);
        return "client:" + access.name() + ":" + routeKey + ":" + clientAddress(request);
    }

    private String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders()
                .set(HttpHeaders.RETRY_AFTER, Long.toString(properties.refillPeriodSeconds()));
        return responseWriter.write(exchange, TOO_MANY_REQUESTS);
    }

    private Bucket bucketFor(String key, long nowMillis) {
        Bucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (bucketAllocationLock) {
            existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }
            trimBuckets(nowMillis);
            if (buckets.size() >= properties.maxBuckets()) {
                return null;
            }
            Bucket created = new Bucket(properties.capacity(), nowMillis);
            buckets.put(key, created);
            return created;
        }
    }

    private void trimBuckets(long nowMillis) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(properties, nowMillis));
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
