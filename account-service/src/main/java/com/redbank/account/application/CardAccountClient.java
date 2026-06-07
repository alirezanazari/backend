package com.redbank.account.application;

import com.redbank.contracts.card.CardSummary;
import java.util.UUID;

public interface CardAccountClient {
    CardSummary createDefaultCard(String accountNumber, UUID userId, UUID profileId);
}
