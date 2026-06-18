package com.redbank.contracts.telemetry;

public final class TelemetryHeaders {
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String AUTHENTICATED_USER_ID = "X-Authenticated-User-Id";
    public static final String AUTHENTICATED_PROFILE_ID = "X-Authenticated-Profile-Id";
    public static final String AUTHENTICATED_DEVICE_ID = "X-Authenticated-Device-Id";

    private TelemetryHeaders() {
    }
}
