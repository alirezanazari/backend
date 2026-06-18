package com.redbank.card.web;

import com.redbank.card.application.CardBusinessRuleException;
import com.redbank.card.application.CardConflictException;
import com.redbank.card.application.CardExternalServiceException;
import com.redbank.card.application.CardFeatureDisabledException;
import com.redbank.card.application.CardNotFoundException;
import com.redbank.card.application.CardUnauthorizedException;
import com.redbank.card.application.CardValidationException;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CardValidationException.class)
    ResponseEntity<ApiResponse<Void>> cardValidation(CardValidationException exception, HttpServletRequest request) {
        return response(Status.validationError(exception.details()), request);
    }

    @ExceptionHandler(CardUnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(CardUnauthorizedException exception, HttpServletRequest request) {
        return response(Status.unauthorized(), request);
    }

    @ExceptionHandler(CardNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(CardNotFoundException exception, HttpServletRequest request) {
        return response(Status.notFound(exception.getMessage()), request);
    }

    @ExceptionHandler(CardConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(CardConflictException exception, HttpServletRequest request) {
        return response(Status.conflict(exception.getMessage()), request);
    }

    @ExceptionHandler(CardBusinessRuleException.class)
    ResponseEntity<ApiResponse<Void>> businessRule(CardBusinessRuleException exception, HttpServletRequest request) {
        return response(Status.businessRuleViolation(exception.getMessage()), request);
    }

    @ExceptionHandler(CardFeatureDisabledException.class)
    ResponseEntity<ApiResponse<Void>> featureDisabled(CardFeatureDisabledException exception, HttpServletRequest request) {
        return response(Status.featureDisabled(exception.getMessage()), request);
    }

    @ExceptionHandler(CardExternalServiceException.class)
    ResponseEntity<ApiResponse<Void>> externalService(CardExternalServiceException exception, HttpServletRequest request) {
        return response(Status.serviceUnavailable(exception.getMessage()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();
        return response(Status.validationError(details), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException exception, HttpServletRequest request) {
        List<StatusDetail> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> new StatusDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        "CONSTRAINT_VIOLATION"
                ))
                .toList();
        return response(Status.validationError(details), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(Status.validationError(List.of(
                new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")
        )), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected card request failure", exception);
        return response(Status.internalError(), request);
    }

    private ResponseEntity<ApiResponse<Void>> response(Status status, HttpServletRequest request) {
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    private StatusDetail toDetail(FieldError error) {
        return new StatusDetail(error.getField(), error.getDefaultMessage(), error.getCode());
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(TelemetryMdc.REQUEST_ID);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }
        String requestIdHeader = request.getHeader(TelemetryHeaders.REQUEST_ID);
        return requestIdHeader == null || requestIdHeader.isBlank() ? correlationId(request) : requestIdHeader;
    }

    private String correlationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(TelemetryMdc.CORRELATION_ID);
        if (correlationId instanceof String value && !value.isBlank()) {
            return value;
        }
        String correlationIdHeader = request.getHeader(TelemetryHeaders.CORRELATION_ID);
        return correlationIdHeader == null || correlationIdHeader.isBlank() ? "unknown" : correlationIdHeader;
    }
}
