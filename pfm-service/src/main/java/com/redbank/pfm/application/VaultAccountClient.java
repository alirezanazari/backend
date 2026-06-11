package com.redbank.pfm.application;

import com.redbank.contracts.account.AccountSummary;
import java.util.UUID;

public interface VaultAccountClient {
    AccountSummary create(UUID userId, UUID profileId, String currency);
}
