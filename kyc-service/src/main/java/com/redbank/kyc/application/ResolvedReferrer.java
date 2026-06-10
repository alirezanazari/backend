package com.redbank.kyc.application;

import java.util.UUID;

public record ResolvedReferrer(
        UUID userId,
        String displayName
) {
}
