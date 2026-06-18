package com.redbank.kyc.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import com.redbank.kyc.application.KycBusinessRuleException;
import com.redbank.kyc.application.KycConflictException;
import com.redbank.kyc.application.KycExternalServiceException;
import com.redbank.kyc.application.KycNotFoundException;
import com.redbank.kyc.application.KycUnauthorizedException;
import com.redbank.kyc.application.KycValidationException;
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

    @ExceptionHandler(KycValidationException.class)
    ResponseEntity<ApiResponse<Void>> kycValidation(KycValidationException exception, HttpServletRequest request) {
        return response(Status.validationError(exception.details()), request);
    }

    @ExceptionHandler(KycUnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(KycUnauthorizedException exception, HttpServletRequest request) {
        return response(Status.unauthorized(), request);
    }

    @ExceptionHandler(KycNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(KycNotFoundException exception, HttpServletRequest request) {
        return response(Status.notFound(exception.getMessage()), request);
    }

    @ExceptionHandler(KycConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(KycConflictException exception, HttpServletRequest request) {
        return response(Status.conflict(exception.getMessage()), request);
    }

    @ExceptionHandler(KycBusinessRuleException.class)
    ResponseEntity<ApiResponse<Void>> businessRule(KycBusinessRuleException exception, HttpServletRequest request) {
        return response(Status.businessRuleViolation(exception.getMessage()), request);
    }

    @ExceptionHandler(KycExternalServiceException.class)
    ResponseEntity<ApiResponse<Void>> externalService(
            KycExternalServiceException exception,
            HttpServletRequest request
    ) {
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
        LOGGER.error("Unexpected KYC request failure", exception);
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
