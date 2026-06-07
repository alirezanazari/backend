package com.redbank.card.domain;

import com.redbank.contracts.card.CardStatus;
import java.time.Instant;
import java.util.UUID;

public record CardStatusHistoryRecord(
        UUID id,
        UUID cardId,
        UUID userId,
        UUID profileId,
        CardStatus previousStatus,
        CardStatus newStatus,
        String reason,
        CardActorType actorType,
        String actorId,
        Instant createdAt
) {
}
