package com.redbank.contracts.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelemetrySensitiveFieldsTest {
    @Test
    void blocksSensitiveFieldsDocumentedByTheTelemetryContract() {
        assertThat(
                java.util.List.of(
                        "data.password",
                        "data.otpCode",
                        "headers.Authorization",
                        "data.accessToken",
                        "data.refreshToken",
                        "data.privateToken",
                        "data.serviceToken",
                        "data.encryptionKeyMaterial",
                        "data.phoneNumber",
                        "data.nationalId",
                        "data.accountNumber",
                        "data.cardNumber",
                        "data.cvv",
                        "data.rawKycEvidence",
                        "spring.datasource.password"
                )
        ).allSatisfy(field -> assertThat(TelemetrySensitiveFields.isBlocked(field)).isTrue());
    }

    @Test
    void allowsRequestCompletionTelemetryFields() {
        assertThat(TelemetryFields.REQUEST_COMPLETION_FIELD_NAMES)
                .allSatisfy(field -> assertThat(TelemetrySensitiveFields.isBlocked(field)).isFalse());
    }
}
