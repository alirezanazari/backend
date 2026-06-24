package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.gateway.config.DebugProperties;
import com.redbank.gateway.config.JwtProperties;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayAuthenticationWebFilterTest {
    private static final String SECRET = "gateway-test-signing-secret-at-least-32-bytes";

    @Test
    void rejectsMissingTokenForProtectedRoute() {
        GatewayAuthenticationWebFilter filter = filter(new RejectingJwtService());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/account/list").build()
        );

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void stripsExternalInternalAndIdentityHeadersFromPublicRoute() {
        GatewayAuthenticationWebFilter filter = filter(new RejectingJwtService());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/iam/login")
                        .header(GatewayAuthenticationWebFilter.INTERNAL_SERVICE_TOKEN_HEADER, "spoofed")
                        .header(GatewayAuthenticationWebFilter.USER_ID_HEADER, "spoofed-user")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().containsKey(GatewayAuthenticationWebFilter.INTERNAL_SERVICE_TOKEN_HEADER))
                .isFalse();
        assertThat(forwarded.get().containsKey(GatewayAuthenticationWebFilter.USER_ID_HEADER)).isFalse();
    }

    @Test
    void forwardsOnlyVerifiedIdentityHeaders() {
        GatewayToken token = new GatewayToken(
                "verified-user",
                "verified-profile",
                "verified-device",
                "verified-token",
                List.of("USER"),
                List.of("USER")
        );
        GatewayAuthenticationWebFilter filter = filter(new FixedJwtService(token));
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transfers/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header(GatewayAuthenticationWebFilter.USER_ID_HEADER, "spoofed-user")
                        .header(GatewayAuthenticationWebFilter.INTERNAL_SERVICE_TOKEN_HEADER, "spoofed-service")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getFirst(GatewayAuthenticationWebFilter.USER_ID_HEADER))
                .isEqualTo("verified-user");
        assertThat(forwarded.get().getFirst(GatewayAuthenticationWebFilter.PROFILE_ID_HEADER))
                .isEqualTo("verified-profile");
        assertThat(forwarded.get().containsKey(GatewayAuthenticationWebFilter.INTERNAL_SERVICE_TOKEN_HEADER))
                .isFalse();
    }

    private GatewayAuthenticationWebFilter filter(GatewayJwtService jwtService) {
        return new GatewayAuthenticationWebFilter(
                jwtService,
                new GatewayAccessPolicy(),
                new GatewayResponseWriter(new ObjectMapper())
        );
    }

    private static class RejectingJwtService extends GatewayJwtService {
        RejectingJwtService() {
            super(
                    new JwtProperties("issuer", "audience", 900, SECRET, null),
                    new DebugProperties(false, SECRET, new DebugProperties.MockOtp(false)),
                    new MockEnvironment(),
                    Clock.systemUTC()
            );
        }

        @Override
        public GatewayToken verifyBearerToken(String authorizationHeader) {
            throw new GatewayTokenException();
        }
    }

    private static class FixedJwtService extends RejectingJwtService {
        private final GatewayToken token;

        FixedJwtService(GatewayToken token) {
            this.token = token;
        }

        @Override
        public GatewayToken verifyBearerToken(String authorizationHeader) {
            return token;
        }
    }
}
