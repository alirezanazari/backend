package com.redbank.kyc.application;

import java.util.UUID;

public record ActiveReferrerAccount(
        UUID userId,
        String accountNumber,
        String currency
) {
}
