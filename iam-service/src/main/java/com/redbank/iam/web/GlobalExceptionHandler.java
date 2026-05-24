package com.redbank.iam.web;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusDetail;
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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<StatusDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();

        Status status = Status.validationError(details);
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), null, status));
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
                .body(ApiResponse.failure(requestId(request), null, status));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        Status status = Status.validationError(List.of(new StatusDetail(null, "Request body is missing or malformed", "MALFORMED_BODY")));
        return ResponseEntity.status(status.httpStatus())
                .body(ApiResponse.failure(requestId(request), null, status));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        Status status = Status.internalError();
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
