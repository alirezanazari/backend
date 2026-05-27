package com.redbank.contracts.envelope;

import java.util.List;

public record Status(
        StatusCode code,
        String message,
        Integer httpStatus,
        List<StatusDetail> details
) {
    public Status {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static Status success() {
        return new Status(StatusCode.SUCCESS, "Success", 200, List.of());
    }

    public static Status created() {
        return new Status(StatusCode.CREATED, "Created", 201, List.of());
    }

    public static Status validationError(List<StatusDetail> details) {
        return new Status(StatusCode.VALIDATION_ERROR, "Invalid request", 400, details);
    }

    public static Status unauthorized() {
        return new Status(StatusCode.UNAUTHORIZED, "Unauthorized", 401, List.of());
    }

    public static Status conflict(String message) {
        return new Status(StatusCode.CONFLICT, message, 409, List.of());
    }

    public static Status businessRuleViolation(String message) {
        return new Status(StatusCode.BUSINESS_RULE_VIOLATION, message, 400, List.of());
    }

    public static Status deviceVerificationRequired() {
        return new Status(StatusCode.DEVICE_VERIFICATION_REQUIRED, "Device verification required", 200, List.of());
    }

    public static Status featureDisabled(String message) {
        return new Status(StatusCode.FEATURE_DISABLED, message, 404, List.of());
    }

    public static Status internalError() {
        return new Status(StatusCode.INTERNAL_ERROR, "Internal error", 500, List.of());
    }
}
