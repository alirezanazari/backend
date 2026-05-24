package com.redbank.contracts.envelope;

import jakarta.validation.constraints.Size;

public record AppContext(
        @Size(max = 40) String version,
        @Size(max = 40) String buildNumber,
        Boolean isRooted,
        @Size(max = 20) String language,
        @Size(max = 20) String theme
) {
}
