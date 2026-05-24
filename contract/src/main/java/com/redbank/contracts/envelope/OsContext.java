package com.redbank.contracts.envelope;

import jakarta.validation.constraints.Size;

public record OsContext(
        @Size(max = 40) String name,
        @Size(max = 40) String version
) {
}
