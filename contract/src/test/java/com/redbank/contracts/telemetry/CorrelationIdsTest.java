package com.redbank.contracts.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdsTest {
    @Test
    void acceptsLowRiskSupportIdentifiers() {
        assertThat(CorrelationIds.isValid("req-123_abc.trace:01")).isTrue();
    }

    @Test
    void rejectsBlankControlCharactersAndLongValues() {
        assertThat(CorrelationIds.isValid("")).isFalse();
        assertThat(CorrelationIds.isValid("unsafe value\n")).isFalse();
        assertThat(CorrelationIds.isValid("x".repeat(81))).isFalse();
    }

    @Test
    void normalizeCreatesUuidWhenMissingOrUnsafe() {
        assertThat(CorrelationIds.normalize(null)).hasSize(36);
        assertThat(CorrelationIds.normalize("unsafe value\n")).hasSize(36);
        assertThat(CorrelationIds.normalize("req-123")).isEqualTo("req-123");
    }
}
