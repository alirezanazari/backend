package com.redbank.contracts.envelope;

import jakarta.validation.constraints.Size;

public record DeviceContext(
        @Size(max = 80) String deviceId,
        @Size(max = 120) String model,
        @Size(max = 256) String fingerprint
) {
}