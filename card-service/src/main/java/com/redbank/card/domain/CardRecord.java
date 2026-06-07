package com.redbank.card.domain;

import com.redbank.contracts.card.CardStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record CardRecord(
        UUID id,
        UUID userId,
        UUID profileId,
        String accountNumber,
        String maskedPan,
        String cardToken,
        CardStatus status,
        YearMonth expiresAt,
        String issuanceReference,
        Instant createdAt,
        Instant updatedAt
) {
    public CardRecord withStatus(CardStatus newStatus, Instant changedAt) {
        return new CardRecord(
                id,
                userId,
                profileId,
                accountNumber,
                maskedPan,
                cardToken,
                newStatus,
                expiresAt,
                issuanceReference,
                createdAt,
                changedAt
        );
    }
}
