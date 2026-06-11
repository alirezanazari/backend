package com.redbank.pfm.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.pfm.application.PfmBusinessRuleException;
import com.redbank.pfm.application.PfmConflictException;
import com.redbank.pfm.application.PfmExternalServiceException;
import com.redbank.pfm.application.PfmNotFoundException;
import com.redbank.pfm.application.PfmUnauthorizedException;
import com.redbank.pfm.application.PfmValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PfmValidationException.class)
    ResponseEntity<ApiResponse<Void>> pfmValidation(
            PfmValidationException exception,
            HttpServletRequest request
    ) {
        return response(Status.validationError(exception.details()), request);
    }

    @ExceptionHandler(PfmUnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(
            PfmUnauthorizedException exception,
            HttpServletRequest request
    ) {
        return response(Status.unauthorized(), request);
    }

    @ExceptionHandler(PfmNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(
            PfmNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(Status.notFound(exception.getMessage()), request);
    }

    @ExceptionHandler(PfmConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(
            PfmConflictException exception,
            HttpServletRequest request
    ) {
        return response(Status.conflict(exception.getMessage()), request);
    }

    @ExceptionHandler(PfmBusinessRuleException.class)
    ResponseEntity<ApiResponse<Void>> businessRule(
            PfmBusinessRuleException exception,
            HttpServletRequest request
    ) {
        return response(Status.businessRuleViolation(exception.getMessage()), request);
    }

    @ExceptionHandler(PfmExternalServiceException.class)
    ResponseEntity<ApiResponse<Void>> externalService(
            PfmExternalServiceException exception,
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
        return response(Status.internalError(), request);
    }

    private ResponseEntity<ApiResponse<Void>> response(Status status, HttpServletRequest request) {
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), null, status));
    }

    private StatusDetail toDetail(FieldError error) {
        return new StatusDetail(error.getField(), error.getDefaultMessage(), error.getCode());
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }
}
