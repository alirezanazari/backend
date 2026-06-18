package com.redbank.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.redbank.contracts.telemetry.TelemetryFields;
import com.redbank.contracts.telemetry.TelemetryHeaders;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationWebFilterTest {
    private final CorrelationWebFilter filter = new CorrelationWebFilter("api-gateway");
    private final Logger logger = (Logger) LoggerFactory.getLogger(CorrelationWebFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void preservesValidCorrelationId() {
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(CorrelationWebFilter.HEADER_NAME, "request-123")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getFirst(CorrelationWebFilter.HEADER_NAME)).isEqualTo("request-123");
        assertThat(forwarded.get().getFirst(TelemetryHeaders.REQUEST_ID)).isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME))
                .isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(TelemetryHeaders.REQUEST_ID))
                .isEqualTo("request-123");
    }

    @Test
    void replacesUnsafeCorrelationId() {
        AtomicReference<String> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(CorrelationWebFilter.HEADER_NAME, "unsafe value\n")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders().getFirst(CorrelationWebFilter.HEADER_NAME));
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get()).isNotEqualTo("unsafe value\n").hasSize(36);
    }

    @Test
    void preservesValidRequestIdHeader() {
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(CorrelationWebFilter.HEADER_NAME, "correlation-123")
                        .header(TelemetryHeaders.REQUEST_ID, "request-456")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getFirst(TelemetryHeaders.REQUEST_ID)).isEqualTo("request-456");
        assertThat(exchange.getResponse().getHeaders().getFirst(TelemetryHeaders.REQUEST_ID))
                .isEqualTo("request-456");
    }

    @Test
    void logsOneCompletionEventWithoutSensitiveRequestData() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/iam/login?password=secret-password")
                        .header(CorrelationWebFilter.HEADER_NAME, "correlation-123")
                        .header(TelemetryHeaders.REQUEST_ID, "request-456")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token-secret")
                        .body("""
                                {
                                  "context": {
                                    "requestId": "request-456",
                                    "account": { "accountNumber": "603799123456789012" },
                                    "device": { "deviceId": "device-123" }
                                  },
                                  "data": {
                                    "password": "secret-password",
                                    "otpCode": "111111",
                                    "accessToken": "access-token-secret",
                                    "cardNumber": "6037991234567890",
                                    "nationalId": "1234567890"
                                  }
                                }
                                """)
        );

        StepVerifier.create(filter.filter(exchange, current -> Mono.empty())).verifyComplete();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, TelemetryFields.EVENT_ACTION))
                .isEqualTo(TelemetryFields.REQUEST_COMPLETED_ACTION);
        assertThat(keyValue(event, TelemetryFields.EVENT_DATASET)).isEqualTo("api-gateway");
        assertThat(keyValue(event, TelemetryFields.HTTP_ROUTE)).isEqualTo("/api/v1/iam/login");
        assertThat(keyValue(event, TelemetryFields.CORRELATION_ID)).isEqualTo("correlation-123");
        assertThat(keyValue(event, TelemetryFields.REQUEST_ID)).isEqualTo("request-456");

        String logged = event.getFormattedMessage() + " " + event.getKeyValuePairs();
        assertThat(logged)
                .doesNotContain(
                        "secret-password",
                        "111111",
                        "access-token-secret",
                        "603799123456789012",
                        "6037991234567890",
                        "1234567890"
                );
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs()
                .stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
