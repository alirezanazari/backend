package com.redbank.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayResponseWriter {
    private final ObjectMapper objectMapper;

    public GatewayResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, Status status) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatusCode.valueOf(status.httpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    ApiResponse.failure(requestId(exchange), null, status)
            );
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            byte[] fallback = "{\"context\":{\"requestId\":\"unknown\",\"profileId\":null,"
                    .concat("\"status\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal error\",")
                    .concat("\"httpStatus\":500,\"details\":[]}},\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(fallback)
            ));
        }
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }
}
