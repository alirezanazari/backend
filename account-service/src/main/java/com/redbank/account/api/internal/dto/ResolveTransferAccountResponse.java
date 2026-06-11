package com.redbank.account.api.internal.dto;

import com.redbank.contracts.common.Image;

public record ResolveTransferAccountResponse(
        String accountId,
        String userId,
        String profileId,
        String accountNumber,
        String iban,
        String currency,
        String destinationName,
        Image image
) {
}
