package com.redbank.pfm.domain;

import com.redbank.contracts.common.Image;
import java.time.Instant;
import java.util.UUID;

public record BoxPresetRecord(
        UUID id,
        String language,
        String title,
        Image image,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
