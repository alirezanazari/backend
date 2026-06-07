package com.redbank.account.application;

import com.redbank.contracts.card.CardForm;

public record CardAccountCreationPayload(
        String accountNumber,
        String userId,
        String profileId,
        CardForm form
) {
}
