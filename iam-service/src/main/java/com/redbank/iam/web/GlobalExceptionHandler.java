package com.redbank.iam.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import com.redbank.contracts.telemetry.TelemetryMdc;
import com.redbank.iam.application.IamBusinessRuleException;
import com.redbank.iam.application.IamConflictException;
import com.redbank.iam.application.IamExternalServiceException;
import com.redbank.iam.application.IamLimitExceededException;
import com.redbank.iam.application.IamUnauthorizedException;
import com.redbank.iam.application.IamValidationException;
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

    @ExceptionHandler(IamValidationException.class)
    ResponseEntity<ApiResponse<Void>> iamValidation(IamValidationException exception, HttpServletRequest request) {
        Status status = Status.validationError(exception.details());
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(IamConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(IamConflictException exception, HttpServletRequest request) {
        Status status = Status.conflict(exception.getMessage());
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(IamUnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(IamUnauthorizedException exception, HttpServletRequest request) {
        Status status = Status.unauthorized();
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(IamLimitExceededException.class)
    ResponseEntity<ApiResponse<Void>> limitExceeded(IamLimitExceededException exception, HttpServletRequest request) {
        Status status = Status.limitExceeded(exception.getMessage());
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(IamBusinessRuleException.class)
    ResponseEntity<ApiResponse<Void>> businessRule(IamBusinessRuleException exception, HttpServletRequest request) {
        Status status = Status.businessRuleViolation(exception.getMessage());
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(IamExternalServiceException.class)
    ResponseEntity<ApiResponse<Void>> externalService(IamExternalServiceException exception, HttpServletRequest request) {
        Status status = Status.serviceUnavailable(exception.getMessage());
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();

        Status status = Status.validationError(details);
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
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

        Status status = Status.validationError(details);
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        Status status = Status.validationError(List.of(new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")));
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), correlationId(request), null, status));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected IAM request failure", exception);
        Status status = Status.internalError();
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
