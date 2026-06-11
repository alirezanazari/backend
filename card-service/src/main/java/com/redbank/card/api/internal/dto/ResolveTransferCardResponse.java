package com.redbank.card.api.internal.dto;

public record ResolveTransferCardResponse(
        String cardId,
        String userId,
        String profileId,
        String accountNumber,
        String maskedCardNumber
) {
}
