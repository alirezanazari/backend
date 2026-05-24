package com.redbank.gateway.debug;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DebugTokenRequest(
        @NotBlank @Size(max = 120) String subject,
        @Size(max = 80) String profileId,
        @Size(max = 80) String deviceId,
        @Size(max = 80) String serviceName,
        List<@Size(max = 80) String> scopes,
        @Min(60) @Max(900) Long ttlSeconds
) {
}
