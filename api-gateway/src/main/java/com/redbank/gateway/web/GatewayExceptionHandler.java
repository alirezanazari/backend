package com.redbank.gateway.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import com.redbank.gateway.security.AppEncryptionException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GatewayExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiResponse<Void>> validation(WebExchangeBindException exception, ServerWebExchange exchange) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();

        Status status = Status.validationError(details);
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), correlationId(exchange), null, status));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ApiResponse<Void>> malformed(ServerWebInputException exception, ServerWebExchange exchange) {
        Status status = Status.validationError(List.of(new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")));
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), correlationId(exchange), null, status));
    }

    @ExceptionHandler(AppEncryptionException.class)
    ResponseEntity<ApiResponse<Void>> encryption(AppEncryptionException exception, ServerWebExchange exchange) {
        Status status = Status.validationError(List.of(
                new StatusDetail("data.clientPublicKey", exception.getMessage(), "INVALID_KEY_EXCHANGE")
        ));
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), correlationId(exchange), null, status));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, ServerWebExchange exchange) {
        LOGGER.error("Unexpected gateway request failure", exception);
        Status status = Status.internalError();
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), correlationId(exchange), null, status));
    }

    private StatusDetail toDetail(FieldError error) {
        return new StatusDetail(error.getField(), error.getDefaultMessage(), error.getCode());
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(TelemetryMdc.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(TelemetryHeaders.REQUEST_ID);
        }
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }

    private String correlationId(ServerWebExchange exchange) {
        String correlationId = exchange.getAttribute(TelemetryMdc.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders().getFirst(TelemetryHeaders.CORRELATION_ID);
        }
        return correlationId == null || correlationId.isBlank() ? "unknown" : correlationId;
    }
}
