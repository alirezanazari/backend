package com.redbank.contracts.telemetry;

import java.util.List;

public final class TelemetryFields {
    public static final String EVENT_ACTION = "event.action";
    public static final String EVENT_DATASET = "event.dataset";
    public static final String EVENT_DURATION = "event.duration";
    public static final String HTTP_REQUEST_METHOD = "http.request.method";
    public static final String HTTP_ROUTE = "http.route";
    public static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status_code";
    public static final String CLIENT_ADDRESS = "client.address";
    public static final String CORRELATION_ID = "correlation.id";
    public static final String REQUEST_ID = "request.id";
    public static final String TRACE_ID = "trace.id";
    public static final String SPAN_ID = "span.id";
    public static final String USER_ID = "user.id";
    public static final String PROFILE_ID = "profile.id";
    public static final String DEVICE_ID = "device.id";
    public static final String REQUEST_COMPLETED_ACTION = "http.request.completed";

    public static final List<String> REQUEST_COMPLETION_FIELD_NAMES = List.of(
            EVENT_ACTION,
            EVENT_DATASET,
            EVENT_DURATION,
            HTTP_REQUEST_METHOD,
            HTTP_ROUTE,
            HTTP_RESPONSE_STATUS_CODE,
            CLIENT_ADDRESS,
            CORRELATION_ID,
            REQUEST_ID,
            TRACE_ID,
            SPAN_ID,
            USER_ID,
            PROFILE_ID,
            DEVICE_ID
    );

    private TelemetryFields() {
    }
}
