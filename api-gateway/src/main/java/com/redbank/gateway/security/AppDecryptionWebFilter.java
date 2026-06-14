package com.redbank.gateway.security;

import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "redbank.gateway.app-encryption.enabled", havingValue = "true")
public class AppDecryptionWebFilter implements WebFilter {
    public static final String KEY_EXCHANGE_ID_HEADER = "X-Key-Exchange-Id";
    public static final String NONCE_HEADER = "X-Encryption-Nonce";

    private final KeyExchangeService keyExchangeService;
    private final AppEncryptionProperties properties;
    private final GatewayResponseWriter responseWriter;

    public AppDecryptionWebFilter(
            KeyExchangeService keyExchangeService,
            AppEncryptionProperties properties,
            GatewayResponseWriter responseWriter
    ) {
        this.keyExchangeService = keyExchangeService;
        this.properties = properties;
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals("/api/v1/security/key-exchange")) {
            return chain.filter(exchange);
        }

        String sessionId = exchange.getRequest().getHeaders().getFirst(KEY_EXCHANGE_ID_HEADER);
        String encodedNonce = exchange.getRequest().getHeaders().getFirst(NONCE_HEADER);
        if (sessionId == null && encodedNonce == null) {
            return chain.filter(exchange);
        }
        if (sessionId == null || sessionId.isBlank() || encodedNonce == null || encodedNonce.isBlank()) {
            return invalid(exchange, "Both encryption headers are required", "INCOMPLETE_ENCRYPTION_HEADERS");
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .switchIfEmpty(Mono.error(new AppEncryptionException("Encrypted request body is required")))
                .flatMap(buffer -> decrypt(exchange, chain, sessionId, encodedNonce, buffer))
                .onErrorResume(AppEncryptionException.class,
                        exception -> invalid(exchange, exception.getMessage(), "INVALID_ENCRYPTED_BODY"))
                .onErrorResume(IllegalArgumentException.class,
                        exception -> invalid(exchange, "Encrypted request encoding is invalid", "INVALID_ENCRYPTED_BODY"));
    }

    private Mono<Void> decrypt(
            ServerWebExchange exchange,
            WebFilterChain chain,
            String sessionId,
            String encodedNonce,
            DataBuffer buffer
    ) {
        byte[] encodedBody = new byte[buffer.readableByteCount()];
        buffer.read(encodedBody);
        DataBufferUtils.release(buffer);
        if (encodedBody.length > properties.maxEncryptedBodyBytes()) {
            return invalid(exchange, "Encrypted request body is too large", "ENCRYPTED_BODY_TOO_LARGE");
        }

        byte[] nonce = Base64.getDecoder().decode(encodedNonce);
        byte[] ciphertext = Base64.getDecoder().decode(
                new String(encodedBody, StandardCharsets.US_ASCII).trim()
        );
        byte[] plaintext = keyExchangeService.decrypt(sessionId, nonce, ciphertext);
        if (plaintext.length > properties.maxEncryptedBodyBytes()) {
            return invalid(exchange, "Decrypted request body is too large", "DECRYPTED_BODY_TOO_LARGE");
        }

        ServerHttpRequest decoratedRequest = decryptedRequest(exchange, plaintext);
        return chain.filter(exchange.mutate().request(decoratedRequest).build());
    }

    private ServerHttpRequest decryptedRequest(ServerWebExchange exchange, byte[] plaintext) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(KEY_EXCHANGE_ID_HEADER);
                headers.remove(NONCE_HEADER);
                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setContentLength(plaintext.length);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(exchange.getResponse().bufferFactory().wrap(plaintext));
            }
        };
    }

    private Mono<Void> invalid(ServerWebExchange exchange, String message, String code) {
        Status status = Status.validationError(List.of(
                new StatusDetail(null, message, code)
        ));
        return responseWriter.write(exchange, status);
    }
}
