package com.redbank.gateway.security;

import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.util.List;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthenticationWebFilter implements WebFilter {
    public static final String USER_ID_HEADER = TelemetryHeaders.AUTHENTICATED_USER_ID;
    public static final String PROFILE_ID_HEADER = TelemetryHeaders.AUTHENTICATED_PROFILE_ID;
    public static final String DEVICE_ID_HEADER = TelemetryHeaders.AUTHENTICATED_DEVICE_ID;
    public static final String TOKEN_ID_HEADER = "X-Authenticated-Token-Id";
    public static final String ROLES_HEADER = "X-Authenticated-Roles";
    public static final String SCOPES_HEADER = "X-Authenticated-Scopes";
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private static final List<String> UNTRUSTED_HEADERS = List.of(
            USER_ID_HEADER,
            PROFILE_ID_HEADER,
            DEVICE_ID_HEADER,
            TOKEN_ID_HEADER,
            ROLES_HEADER,
            SCOPES_HEADER,
            INTERNAL_SERVICE_TOKEN_HEADER
    );

    private final GatewayJwtService jwtService;
    private final GatewayAccessPolicy accessPolicy;
    private final GatewayResponseWriter responseWriter;

    public GatewayAuthenticationWebFilter(
            GatewayJwtService jwtService,
            GatewayAccessPolicy accessPolicy,
            GatewayResponseWriter responseWriter
    ) {
        this.jwtService = jwtService;
        this.accessPolicy = accessPolicy;
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest sanitizedRequest = sanitize(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();
        GatewayAccessPolicy.Access access = accessPolicy.access(
                sanitizedRequest.getMethod(),
                sanitizedRequest.getPath().value()
        );
        if (access == GatewayAccessPolicy.Access.NOT_GATEWAY_API
                || access == GatewayAccessPolicy.Access.PUBLIC) {
            return chain.filter(sanitizedExchange);
        }

        GatewayToken token;
        try {
            token = jwtService.verifyBearerToken(
                    sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)
            );
        } catch (GatewayTokenException exception) {
            return responseWriter.write(sanitizedExchange, Status.unauthorized());
        }
        if (!accessPolicy.permits(access, token)) {
            Status forbidden = new Status(StatusCode.FORBIDDEN, "Forbidden", 403, List.of());
            return responseWriter.write(sanitizedExchange, forbidden);
        }

        ServerHttpRequest trustedRequest = sanitizedRequest.mutate()
                .headers(headers -> addTrustedHeaders(headers, token))
                .build();
        return chain.filter(sanitizedExchange.mutate().request(trustedRequest).build());
    }

    private ServerHttpRequest sanitize(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> UNTRUSTED_HEADERS.forEach(headers::remove))
                .build();
    }

    private void addTrustedHeaders(HttpHeaders headers, GatewayToken token) {
        headers.set(USER_ID_HEADER, token.subject());
        setOrRemove(headers, PROFILE_ID_HEADER, token.profileId());
        setOrRemove(headers, DEVICE_ID_HEADER, token.deviceId());
        setOrRemove(headers, TOKEN_ID_HEADER, token.tokenId());
        setOrRemove(headers, ROLES_HEADER, String.join(",", token.roles()));
        setOrRemove(headers, SCOPES_HEADER, String.join(",", token.scopes()));
    }

    private void setOrRemove(HttpHeaders headers, String name, String value) {
        if (value == null || value.isBlank()) {
            headers.remove(name);
        } else {
            headers.set(name, value);
        }
    }
}
