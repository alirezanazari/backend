package com.redbank.account.application;

import java.util.UUID;

public interface LedgerAccountClient {
    boolean createAccount(UUID eventId, String accountNumber, UUID ownerUserId, String currency);

    boolean close(UUID eventId, String accountNumber);

    boolean reopen(UUID eventId, String accountNumber);
}
