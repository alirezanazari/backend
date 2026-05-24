package com.redbank.contracts.envelope;

public record StatusDetail(
        String field,
        String message,
        String code
) {
}
