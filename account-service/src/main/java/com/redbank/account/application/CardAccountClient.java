package com.redbank.account.application;

import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.card.CardForm;
import java.util.UUID;

public interface CardAccountClient {
    CardSummary createDefaultCard(String accountNumber, UUID userId, UUID profileId, CardForm form);

    boolean close(String accountNumber);

    boolean reopen(String accountNumber);
}
