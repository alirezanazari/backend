package com.redbank.contracts.envelope;

import jakarta.validation.constraints.Size;

public record NetworkContext(
        @Size(max = 64) String ipAddress,
        @Size(max = 40) String connectionType,
        @Size(max = 80) String operator
) {
}
