package com.redbank.transfer.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import com.redbank.transfer.application.TransferBusinessRuleException;
import com.redbank.transfer.application.TransferConflictException;
import com.redbank.transfer.application.TransferDuplicateRequestException;
import com.redbank.transfer.application.TransferExternalServiceException;
import com.redbank.transfer.application.TransferFeatureDisabledException;
import com.redbank.transfer.application.TransferInsufficientFundsException;
import com.redbank.transfer.application.TransferNotFoundException;
import com.redbank.transfer.application.TransferUnauthorizedException;
import com.redbank.transfer.application.TransferValidationException;
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

    @ExceptionHandler(TransferValidationException.class)
    ResponseEntity<ApiResponse<Void>> transferValidation(
            TransferValidationException exception,
            HttpServletRequest request
    ) {
        return response(Status.validationError(exception.details()), request);
    }

    @ExceptionHandler(TransferUnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(
            TransferUnauthorizedException exception,
            HttpServletRequest request
    ) {
        return response(Status.unauthorized(), request);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(
            TransferNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(Status.notFound(exception.getMessage()), request);
    }

    @ExceptionHandler(TransferConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(
            TransferConflictException exception,
            HttpServletRequest request
    ) {
        return response(Status.conflict(exception.getMessage()), request);
    }

    @ExceptionHandler(TransferDuplicateRequestException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(
            TransferDuplicateRequestException exception,
            HttpServletRequest request
    ) {
        return response(new Status(
                StatusCode.DUPLICATE_REQUEST,
                exception.getMessage(),
                409,
                List.of()
        ), request);
    }

    @ExceptionHandler(TransferBusinessRuleException.class)
    ResponseEntity<ApiResponse<Void>> businessRule(
            TransferBusinessRuleException exception,
            HttpServletRequest request
    ) {
        return response(Status.businessRuleViolation(exception.getMessage()), request);
    }

    @ExceptionHandler(TransferFeatureDisabledException.class)
    ResponseEntity<ApiResponse<Void>> featureDisabled(
            TransferFeatureDisabledException exception,
            HttpServletRequest request
    ) {
        return response(Status.featureDisabled(exception.getMessage()), request);
    }

    @ExceptionHandler(TransferInsufficientFundsException.class)
    ResponseEntity<ApiResponse<Void>> insufficientFunds(
            TransferInsufficientFundsException exception,
            HttpServletRequest request
    ) {
        return response(Status.insufficientFunds(exception.getMessage()), request);
    }

    @ExceptionHandler(TransferExternalServiceException.class)
    ResponseEntity<ApiResponse<Void>> externalService(
            TransferExternalServiceException exception,
            HttpServletRequest request
    ) {
        return response(new Status(
                StatusCode.EXTERNAL_SERVICE_ERROR,
                exception.getMessage(),
                502,
                List.of()
        ), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();
        return response(Status.validationError(details), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraint(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
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
    ResponseEntity<ApiResponse<Void>> malformed(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(Status.validationError(List.of(
                new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")
        )), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected transfer request failure", exception);
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
