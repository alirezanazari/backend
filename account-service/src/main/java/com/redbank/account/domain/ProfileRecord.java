package com.redbank.account.domain;

import com.redbank.contracts.account.ProfileType;
import java.time.Instant;
import java.util.UUID;

public record ProfileRecord(
        UUID id,
        UUID userId,
        String selectedJobId,
        ProfileType type,
        String displayName,
        String displayNameNormalized,
        String imageLightUrl,
        String imageDarkUrl,
        String imageAlt,
        ProfileStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
        public ProfileRecord update(
                String displayName,
                String displayNameNormalized,
                String imageLightUrl,
                String imageDarkUrl,
                String imageAlt
        ) {
                return new ProfileRecord(id, userId, selectedJobId, type, displayName, displayNameNormalized, imageLightUrl, imageDarkUrl, imageAlt, status, active, createdAt, updatedAt);
        }
}
