package com.redbank.kyc.domain;

import java.time.Instant;

public record JobRecord(
        String id,
        String code,
        String title,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
