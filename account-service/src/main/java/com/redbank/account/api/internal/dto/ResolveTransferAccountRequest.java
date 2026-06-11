package com.redbank.account.api.internal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResolveTransferAccountRequest(
        @Pattern(regexp = "\\d{18}") String accountNumber,
        @Size(max = 34) String iban,
        String userId,
        String profileId,
        Boolean requireOwnership
) {
}
