package com.redbank.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record DeviceRecord(
        UUID id,
        UUID userId,
        String deviceFingerprint,
        String deviceName,
        String os,
        String appVersion,
        DeviceStatus status,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
