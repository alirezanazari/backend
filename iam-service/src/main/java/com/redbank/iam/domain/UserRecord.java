package com.redbank.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(
        UUID id,
        String username,
        String phoneNumber,
        String nationalId,
        String firstName,
        String lastName,
        String countryCode,
        String province,
        String city,
        String address,
        String postalCode,
        String selectedJobId,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
