package com.redbank.card.application;

import java.util.UUID;

public interface AccountEligibilityClient {
    EligibleAccount requireEligible(String accountNumber, UUID userId, UUID profileId);
}
