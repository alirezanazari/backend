package com.redbank.account.application;

import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.card.CardForm;
import java.util.UUID;

public interface CardAccountClient {
    CardSummary createDefaultCard(UUID eventId, String accountNumber, UUID userId, UUID profileId, CardForm form);

    boolean close(UUID eventId, String accountNumber);

    boolean reopen(UUID eventId, String accountNumber);
}
