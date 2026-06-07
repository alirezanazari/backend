package com.redbank.card.domain;

import java.time.Instant;
import java.util.UUID;

public record CardOrderRecord(
        UUID id,
        UUID userId,
        UUID profileId,
        String accountNumber,
        String deliveryCountryCode,
        String deliveryProvince,
        String deliveryCity,
        String deliveryAddress,
        String deliveryPostalCode,
        String reason,
        CardOrderStatus status,
        UUID cardId,
        Instant createdAt,
        Instant updatedAt
) {
}
