package com.redbank.gateway.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GatewayExceptionHandler {
    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiResponse<Void>> validation(WebExchangeBindException exception, ServerWebExchange exchange) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();

        Status status = Status.validationError(details);
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), null, status));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ApiResponse<Void>> malformed(ServerWebInputException exception, ServerWebExchange exchange) {
        Status status = Status.validationError(List.of(new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")));
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), null, status));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, ServerWebExchange exchange) {
        Status status = Status.internalError();
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(exchange), null, status));
    }

    private StatusDetail toDetail(FieldError error) {
        return new StatusDetail(error.getField(), error.getDefaultMessage(), error.getCode());
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }
}
