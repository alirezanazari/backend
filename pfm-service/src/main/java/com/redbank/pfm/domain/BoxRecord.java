package com.redbank.pfm.domain;

import com.redbank.contracts.common.Image;
import com.redbank.contracts.pfm.BoxStatus;
import java.time.Instant;
import java.util.UUID;

public record BoxRecord(
        UUID id,
        UUID userId,
        UUID profileId,
        String accountNumber,
        String title,
        UUID presetId,
        Image image,
        BoxStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public BoxRecord withDisplayMetadata(
            String title,
            UUID presetId,
            Image image,
            Instant updatedAt
    ) {
        return new BoxRecord(
                id,
                userId,
                profileId,
                accountNumber,
                title,
                presetId,
                image,
                status,
                createdAt,
                updatedAt,
                closedAt
        );
    }
}
