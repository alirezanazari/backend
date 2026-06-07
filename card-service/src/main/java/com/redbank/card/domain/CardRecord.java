package com.redbank.card.domain;

import com.redbank.contracts.card.CardStatus;
import com.redbank.contracts.card.CardForm;
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
        CardForm form,
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
                form,
                newStatus,
                expiresAt,
                issuanceReference,
                createdAt,
                changedAt
        );
    }

    public CardRecord withForm(CardForm newForm, Instant changedAt) {
        return new CardRecord(
                id,
                userId,
                profileId,
                accountNumber,
                maskedPan,
                cardToken,
                newForm,
                status,
                expiresAt,
                issuanceReference,
                createdAt,
                changedAt
        );
    }
}
